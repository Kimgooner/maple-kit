"""Conservative option grammar. Conditions are represented, never evaluated."""
from dataclasses import dataclass, replace
from importlib.resources import files
from types import MappingProxyType
import json
import re
from .raw import Kind, checked_decimal, MapleError

_ALIASES = json.loads(files('maple_library').joinpath('option-aliases.json').read_text())
_LABEL = '|'.join(re.escape(x) for x in sorted(_ALIASES,key=lambda x:(-len(x),x)))
_RULES = json.loads(files('maple_library').joinpath('option-rules.json').read_text())
_NUMBER = r'[+-]?[0-9]+(?:\.[0-9]+)?'
_SIMPLE = re.compile(rf'(?P<label>{_LABEL})\s*:?\s*(?P<value>{_NUMBER})\s*(?P<unit>%|초|레벨)?\s*(?P<verb>증가|감소)?\Z')
_CHANCE = re.compile(rf'공격 시\s*(?P<chance>{_NUMBER})%\s*확률로\s*(?P<effect>.+)\Z')
_AP = re.compile(rf'AP를 직접 투자한 (STR|DEX|INT|LUK)의\s*({_NUMBER})% 만큼 (STR|DEX|INT|LUK) 증가\Z')
_PER_LEVEL = re.compile(rf'캐릭터 기준\s*9레벨\s*당\s*(STR|DEX|INT|LUK)\s*({_NUMBER})\s*(?:증가)?\Z')

@dataclass(frozen=True, slots=True)
class Option:
    raw_text: str
    start: int
    end: int
    status: str
    targets: tuple = ()
    value: object = None
    unit: str = 'UNKNOWN'
    conditions: tuple = ()
    expression: tuple = ()
    rule_id: str = 'unknown'

@dataclass(frozen=True, slots=True)
class LocatedOption:
    pointer: str
    option: Option


def _single(text, start, end):
    body = text[start:end]
    for rule in _RULES:
        match=re.fullmatch(rule['pattern'],body)
        if match:
            inner=match[rule['effect_group']]
            # Only a single fully understood stat clause can inherit a scope.
            if _SIMPLE.fullmatch(inner):
                option=_single(inner,0,len(inner))
                expression=dict(option.expression);expression.update(rule.get('expression',{}))
                if 'duration_group' in rule:
                    duration=match[rule['duration_group']];checked_decimal(duration)
                    expression['duration_seconds']=duration
                return replace(option,raw_text=body,start=start,end=end,conditions=tuple(rule['conditions'])+option.conditions,expression=tuple(expression.items()),rule_id=rule['id'])
            return Option(body,start,end,'UNKNOWN')
    match = _AP.fullmatch(body)
    if match:
        return Option(body,start,end,'KNOWN',(match[3],),None,'PERCENT',(),
                      (('kind','PROPORTIONAL_TO_STAT'),('basis','INVESTED_AP.'+match[1]),('ratio',match[2])), 'ap-ratio')
    match = _PER_LEVEL.fullmatch(body)
    if match:
        return Option(body,start,end,'KNOWN',(match[1],),checked_decimal(match[2]),'FLAT',(),(('kind','PER_LEVEL'),('levels','9')),'per-level')
    match = _CHANCE.fullmatch(body)
    chance = None
    effect = body
    if match:
        chance = match['chance']
        if not 0 <= checked_decimal(chance) <= 100:
            return Option(body,start,end,'AMBIGUOUS')
        effect = match['effect']
    match = _SIMPLE.fullmatch(effect)
    if not match:
        return Option(body,start,end,'UNKNOWN')
    targets = tuple(_ALIASES[match['label']])
    unit = {'%':'PERCENT','초':'SECONDS','레벨':'LEVEL',None:'FLAT'}[match['unit']]
    value = checked_decimal(match['value'])
    conditions = ()
    if targets == ('BOSS_DAMAGE',): conditions += ('TARGET_BOSS',)
    if targets == ('NORMAL_DAMAGE',): conditions += ('TARGET_NORMAL_MONSTER',)
    if chance is not None: conditions += ('ON_ATTACK','CHANCE_PERCENT='+chance)
    expression = (('direction','DECREASE'),) if match['verb']=='감소' else ()
    return Option(body,start,end,'KNOWN',targets,value,unit,conditions,expression,'stat')


def decode_options(text):
    if not isinstance(text,str):
        from .raw import MapleError
        raise MapleError('INVALID_REQUEST')
    # Only split after a completed numeric clause. Compound HP,MP / ATTACK,MAGIC remains intact.
    boundaries = [0]
    segment = 0
    depth=0
    for i,c in enumerate(text):
        if c=='[': depth+=1
        elif c==']': depth=max(0,depth-1)
        if depth==0 and (c in '\r\n' or (c==',' and _SIMPLE.fullmatch(text[segment:i].strip()) and re.match(rf'\s*(?:{_LABEL})\s*:?\s*{_NUMBER}',text[i+1:]))):
            boundaries.extend((i,i+1));segment=i+1
    boundaries.append(len(text))
    options=[]
    for start,end in zip(boundaries[::2],boundaries[1::2]):
        while start<end and text[start].isspace():start+=1
        while end>start and text[end-1].isspace():end-=1
        if start<end:
            try: options.append(_single(text,start,end))
            except MapleError: options.append(Option(text[start:end],start,end,'AMBIGUOUS'))
    return tuple(options)

# Only effect-bearing fields; names/icons/identifiers are not parsed as stat prose.
_EFFECT_KEYS = frozenset(('skill_effect','stat_increase','ability_value','title_description','soul_option',
                          'option_value','effect','stat','name'))

def document_options(document):
    from .model import escape, DocumentStatus
    if document.status is not DocumentStatus.FETCHED: return ()
    found=[]
    def walk(raw,path,parent_key=''):
        if raw.kind is Kind.OBJECT:
            # Structured option_type + option_value preserves the source value pointer.
            t,v=raw.child('option_type'),raw.child('option_value')
            structured=t is not None and t.kind is Kind.STRING and v is not None and v.kind in (Kind.STRING,Kind.NUMBER)
            if structured:
                for option in decode_options(t.value+' '+v.value):
                    # Interpret with the type sibling, but keep spans in the actual value field.
                    found.append(LocatedOption(path+'/option_value',replace(option,raw_text=v.value,start=0,end=len(v.value),rule_id='structured:'+option.rule_id)))
            for key,value in raw.value.items():
                p=path+'/'+escape(key)
                effect = key in _EFFECT_KEYS or re.fullmatch(r'(?:additional_)?potential_option_[1-3]|soul_potential_option_[1-3]',key)
                # 'name' is only an effect under artifact effect entries, not entity names.
                if key=='name':effect='union_artifact_effect/' in path
                if key=='option_value' and structured:continue
                if value.kind is Kind.STRING and effect:
                    for option in decode_options(value.value):found.append(LocatedOption(p,option))
                else:walk(value,p,key)
        elif raw.kind is Kind.ARRAY:
            for i,value in enumerate(raw.value):
                p=path+'/'+str(i)
                if value.kind is Kind.STRING and parent_key in ('union_raider_stat','union_state_stat','union_occupied_stat'):
                    for option in decode_options(value.value):found.append(LocatedOption(p,option))
                else:walk(value,p,parent_key)
    walk(document.raw,'')
    return tuple(found)
