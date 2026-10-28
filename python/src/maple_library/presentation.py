"""Framework-independent presentation helpers. Never perform I/O or game calculations."""
from __future__ import annotations
from dataclasses import dataclass
from decimal import Decimal
from importlib.resources import files
from typing import Generic, TypeVar
import json
from .model import EntityView, FieldState, Source, DocumentStatus, number_field
from .raw import Kind, MapleError
from .options import decode_options

CONTRACT = json.loads(files('maple_library').joinpath('presentation.json').read_text())


def decimal_text(value):
    text = format(value, 'f')
    return (text.rstrip('0').rstrip('.') if '.' in text else text) if value else '0'


def signed(value, unit='FLAT', base=False):
    return ('' if base or value < 0 else '+') + decimal_text(value) + ('%' if unit == 'PERCENT' else '')


def field_at(entity, path):
    if not path:
        from .model import FieldValue
        return FieldValue(FieldState.VALUE, entity, entity.raw, entity.pointer)
    # Alternatives only account for documented spelling differences; explicit null is retained.
    for candidate in path.split('|'):
        current = entity
        parts = candidate.split('/')
        for token in parts[:-1]:
            value = current.object(token)
            if value.state is not FieldState.VALUE:
                break
            current = value.value
        else:
            value = current.field(parts[-1])
        if value.state is not FieldState.MISSING:
            return value
    return value


def convert(field, kind):
    if field.state in (FieldState.MISSING, FieldState.NULL):
        return None
    if field.state is FieldState.INVALID:
        raise MapleError('INVALID_FIELD')
    raw = field.raw
    if kind in ('integer', 'number'):
        number = number_field(raw, field.pointer)
        if number.state is not FieldState.VALUE:
            raise MapleError('INVALID_FIELD')
        if kind == 'number':
            return number.value
        if number.value != number.value.to_integral_value() or not -2147483648 <= number.value <= 2147483647:
            raise MapleError('INVALID_FIELD')
        return int(number.value)
    if kind == 'option':
        return OptionLine(convert(field, 'text'))
    if kind == 'text':
        if raw.kind is not Kind.STRING:
            raise MapleError('INVALID_FIELD')
        return raw.value
    if kind.startswith('list:'):
        if raw.kind is not Kind.ARRAY:
            raise MapleError('INVALID_FIELD')
        from .model import FieldValue
        return tuple(convert(FieldValue(FieldState.VALUE, raw=v, pointer=field.pointer+'/'+str(i)), kind[5:])
                     if v.kind is not Kind.NULL else _invalid() for i, v in enumerate(raw.value))
    if raw.kind is not Kind.OBJECT:
        raise MapleError('INVALID_FIELD')
    from . import friendly
    return getattr(friendly, kind)(EntityView(raw, field.pointer))


def _invalid():
    raise MapleError('INVALID_FIELD')


def json_value(value):
    if isinstance(value, (Node, OptionLine, Preset)):
        return value.to_view()
    if isinstance(value, Decimal):
        return decimal_text(value)
    if isinstance(value, (tuple, list)):
        return [json_value(v) for v in value]
    return value


class _ImmutableFacade:
    __slots__ = ()
    def __setattr__(self, name, value):
        raise AttributeError('Models are read-only')
    def __delattr__(self, name):
        raise AttributeError('Models are read-only')


class Node(_ImmutableFacade):
    """Immutable typed facade over the original EntityView; low-level states stay available."""
    __slots__ = ('detail', '_prefix')
    detail: EntityView
    _prefix: str

    def __init__(self, detail: EntityView, _prefix: str = ''):
        object.__setattr__(self, 'detail', detail)
        object.__setattr__(self, '_prefix', _prefix)
        if self.detail.raw.kind is not Kind.OBJECT:
            raise MapleError('INVALID_FIELD')

    def _field(self, path):
        return field_at(self.detail, '|'.join(self._prefix+p for p in path.split('|')) if self._prefix else path)

    def _get(self, name):
        spec = CONTRACT['models'][type(self).__name__][name]
        return convert(self._field(spec['path']), spec['type'])

    def to_view(self):
        result, diagnostics = {}, []
        for name, spec in CONTRACT['models'][type(self).__name__].items():
            try:
                result[name] = json_value(self._get(name))
            except MapleError:
                result[name] = None
                diagnostics.append('INVALID_FIELD:'+self._field(spec['path']).pointer)
        result['diagnostics'] = diagnostics
        return decorate(self, result)

    def to_json(self):
        return json.dumps(self.to_view(), ensure_ascii=False, allow_nan=False)


@dataclass(frozen=True, slots=True)
class OptionLine:
    text: str
    slot: int | None = None

    @property
    def effects(self):
        return decode_options(self.text)

    @property
    def parse_status(self):
        effects = self.effects
        return 'KNOWN' if effects and all(e.status == 'KNOWN' for e in effects) else 'UNKNOWN'

    def to_view(self):
        return {'slot': self.slot, 'text': self.text, 'parse_status': self.parse_status}


def text_field(node, path):
    return convert(field_at(node.detail, path), 'text')


def potential(node, prefix='potential_option'):
    grade_key = 'soul_potential_grade' if prefix == 'soul_potential_option' else prefix+'_grade'
    grade = text_field(node, grade_key)
    lines = tuple(OptionLine(text, slot) for slot in range(1, 4)
                  if (text := text_field(node, prefix+'_'+str(slot))) is not None and text != '')
    flag = None if prefix == 'soul_potential_option' else text_field(node, prefix+'_flag')
    amplification = convert(field_at(node.detail, 'soul_potential_amplified_grade'), 'integer') if prefix == 'soul_potential_option' else None
    if grade is None and not lines and flag is None and amplification is None:
        return None
    return Potential(grade, lines, flag, amplification, {'potential_option':'잠재능력', 'additional_potential_option':'에디셔널 잠재능력', 'soul_potential_option':'소울 잠재능력'}[prefix])


@dataclass(frozen=True, slots=True)
class Potential:
    grade: str | None
    lines: tuple[OptionLine, ...]
    flag: str | None
    amplification_level: int | None
    title: str

    def to_view(self):
        grades = {'레어':('RARE','레어'),'에픽':('EPIC','에픽'),'유니크':('UNIQUE','유니크'),'레전드리':('LEGENDARY','레전')}
        grade, short = grades.get(self.grade, (self.grade, self.grade))
        return {'title':self.title,'grade':grade,'grade_label':self.grade,'grade_short_label':short,
                'flag':self.flag,'amplification_level':self.amplification_level,
                'amplification_text':None if self.amplification_level is None else f'증폭 {self.amplification_level}단계',
                'summary_text':', '.join(x.text for x in self.lines),'lines':[x.to_view() for x in self.lines]}


@dataclass(frozen=True, slots=True)
class Soul:
    name: str | None
    option_text: str | None
    attack_power: Decimal | None
    magic_power: Decimal | None
    active_text: str | None
    potential: Potential | None

    def to_view(self):
        lines = [] if self.option_text is None else [OptionLine(self.option_text).to_view()]
        # soul_pad/mad remain separate provided values, never added to another option or total.
        return {'name':self.name,'summary_text':', '.join(x for x in (self.name,self.option_text) if x),
                'option_lines':lines,'attack_power':json_value(self.attack_power),'magic_power':json_value(self.magic_power),
                'active_text':self.active_text,'potential':None if self.potential is None else self.potential.to_view()}


def soul(node):
    name, text, active = (text_field(node, k) for k in ('soul_name','soul_option','soul_active'))
    attack, magic = (convert(field_at(node.detail, k), 'number') for k in ('soul_pad','soul_mad'))
    pot = potential(node, 'soul_potential_option')
    if all(v is None for v in (name,text,active,attack,magic,pot)):
        return None
    return Soul(name,text,attack,magic,active,pot)


def stat_rows(node, include_zero=False):
    rows=[]
    for spec in CONTRACT['stats']:
        if spec['name'] not in CONTRACT['models'][type(node).__name__]:
            continue
        value = node._get(spec['name'])
        if value is None or value == 0 and not include_zero:
            continue
        text = signed(value,spec['unit'])
        rows.append({'key':spec['key'],'label':spec['label'],'value':decimal_text(value),
                     'unit':spec['unit'],'value_text':text,'text':spec['label']+' '+text})
    return rows


def item_rows(node):
    stats=node._get('stats')
    if stats is None:
        return None
    rows=stat_rows(stats)
    options=node._get('options')
    for row in rows:
        name=next(s['name'] for s in CONTRACT['stats'] if s['key']==row['key'])
        parts=[]
        for source,label in [('base','기본'),('starforce','스타포스'),('scroll','주문서 등 기타'),('additional','추가옵션'),('exceptional','익셉셔널')]:
            block=options._get(source)
            value=None if block is None else block._get(name)
            if value is not None and (value != 0 or source=='base'):
                parts.append({'source':source.upper(),'label':label,'value':decimal_text(value),'text':signed(value,row['unit'],source=='base')})
        row['components']=parts
        row['breakdown_text']=' '.join(x['text'] for x in parts)
    return rows


def decorate(node, view):
    kind=type(node).__name__
    def safely(key, fn):
        try: view[key]=fn()
        except MapleError:
            view[key]=None
            view['diagnostics'].append('INVALID_FIELD:'+node.detail.pointer+'/'+key)
    if 'icon' in view:
        view['icon_url']=view.pop('icon')
    if kind in ('StatBlock','SymbolStats'):
        safely('rows',lambda:stat_rows(node))
    if kind=='Item':
        safely('stat_rows',lambda:item_rows(node))
        for key,prefix in [('potential','potential_option'),('additional_potential','additional_potential_option')]:
            safely(key,lambda prefix=prefix:None if (p:=potential(node,prefix)) is None else p.to_view())
        safely('soul',lambda:None if (s:=soul(node)) is None else s.to_view())
        view.pop('stats')
        view.pop('options')
        view['category']=view['part']
        upgrade=view['scrolls']['upgrade_count']
        view['display_name']=None if view['name'] is None else view['name']+(f' (+{upgrade})' if upgrade is not None and upgrade>0 else '')
        view['badges']=[]
        if view['starforce'] is not None:
            view['badges'].append({'key':'STARFORCE','label':'스타포스','value':view['starforce'],'text':str(view['starforce'])+'성'})
        if upgrade is not None:
            view['badges'].append({'key':'SCROLL_UPGRADE','label':'주문서 강화','value':upgrade,'text':('+' if upgrade>=0 else '')+str(upgrade)})
        view['enhancement']=view.pop('scrolls')
    if kind=='Enhancement':
        u,r,c=(view[k] for k in ('upgrade_count','remaining_count','resilience_count'))
        view['text']=None if any(v is None for v in (u,r,c)) else f'주문서 강화 {u}회 (잔여 {r}회, 복구 가능 {c}회)'
    if kind=='Symbol':
        safely('stat_rows',lambda:stat_rows(node._get('stats')))
        view.pop('stats')
        view['force_text']=None if view['force'] is None else '포스 '+view['force']
    if 'level' in view:
        view['level_text']=None if view['level'] is None else 'Lv. '+str(view['level'])
    if kind=='Pet':view['slot']=node.slot
    if 'text' in CONTRACT['models'][kind]:
        safely('parse_status',lambda:None if (t:=node._get('text')) is None else OptionLine(t).parse_status)
    if kind=='NamedOption':
        view['text']=None if view['name'] is None or view['value'] is None else view['name']+' '+view['value']
    if kind=='Pets':
        safely('items',lambda:json_value(pets(node)))
        safely('shared',lambda:json_value(pets(node,True)))
    groups = dict(CONTRACT.get('extra_presets',{}).get(kind,{}))
    if kind in CONTRACT['presets']:groups['presets']=CONTRACT['presets'][kind]
    if kind=='HyperStats':
        safely('items',lambda:json_value(node.items))
    for group,(prefix,type_name,shape) in groups.items():
        view[group]={}
        for key in node.detail.raw.value:
            suffix=key.removeprefix(prefix)
            if key.startswith(prefix) and suffix.isascii() and suffix.isdigit() and 0<int(suffix)<=2147483647:
                try:view[group][suffix]=json_value(preset(node,int(suffix),group))
                except MapleError:
                    view[group][suffix]=None
                    view['diagnostics'].append('INVALID_FIELD:'+node.detail.pointer+'/'+key)
    return view


T = TypeVar("T", bound=Node)


@dataclass(frozen=True, slots=True)
class Preset(Generic[T]):
    number: int
    items: tuple[T,...] | None
    remaining_points: Decimal | None = None
    def to_view(self):
        return {'number':self.number,'items':json_value(self.items),'remaining_points':json_value(self.remaining_points)}


def preset(node, number, group="presets"):
    if type(number) is not int or not 1<=number<=2147483647:
        raise MapleError('INVALID_REQUEST')
    prefix,kind,shape=CONTRACT['presets'][type(node).__name__] if group=='presets' else CONTRACT['extra_presets'][type(node).__name__][group]
    field=field_at(node.detail,prefix+str(number))
    if field.state is FieldState.MISSING:raise MapleError('FIELD_UNAVAILABLE')
    value=convert(field,('list:' if shape=='list' else '')+kind)
    if shape=='object':return value
    remaining=convert(field_at(node.detail,prefix+str(number)+'_remain_point'),'number') if type(node).__name__=='HyperStats' else None
    return Preset(number,value,remaining)


def pets(node, shared=False):
    from .friendly import Pet
    name_prefix='world_share_pet_' if shared else 'pet_'
    if not any(name_prefix+str(slot)+'_name' in node.detail.raw.value for slot in range(1,4)):
        return None
    result=[]
    for slot in range(1,4):
        prefix=('world_share_pet_' if shared else 'pet_')+str(slot)+'_'
        fields={k[len(prefix):]:v for k,v in node.detail.raw.value.items() if k.startswith(prefix)}
        if not fields:continue
        pet=Pet(node.detail,prefix)
        if pet.name is not None:result.append(pet)
    return tuple(result)


class SourceGroup(_ImmutableFacade):
    __slots__=('_character','_members')
    def __init__(self, character, members):
        from types import MappingProxyType
        object.__setattr__(self, '_character', character)
        object.__setattr__(self, '_members', MappingProxyType(dict(members)))
    def __getattr__(self,name):
        if name not in self._members:raise AttributeError(name)
        return self._character.source(Source[self._members[name]])
    def to_view(self):
        return {key:self._character._view_source(Source[source]) for key,source in self._members.items()}


class CharacterBase(_ImmutableFacade):
    __slots__ = ('sheet',)
    def __init__(self, sheet):
        from .model import CharacterSheet
        if not isinstance(sheet, CharacterSheet):raise MapleError('INVALID_REQUEST')
        object.__setattr__(self, 'sheet', sheet)
    def source(self,source):
        if not isinstance(source, Source):raise MapleError("INVALID_REQUEST")
        if source.value not in CONTRACT['sources']:raise MapleError('INVALID_REQUEST')
        doc=self.sheet.document(source)
        # Document.view is deliberately strict for NOT_REQUESTED, FAILED and UNSUPPORTED.
        from . import friendly
        return getattr(friendly,CONTRACT['sources'][source.value][1])(doc.view)
    def _view_source(self,source):
        return self.source(source).to_view() if self.sheet.document(source).status is DocumentStatus.FETCHED else None
    @property
    def union(self):
        return SourceGroup(self,{k:'UNION_'+k.upper() for k in ('basic','raider','artifact','champion')})
    @property
    def skills(self):
        return SourceGroup(self,{'zero':'SKILL_0','hyper_passive':'SKILL_HYPER_PASSIVE','hyper_active':'SKILL_HYPER_ACTIVE','fifth':'SKILL_5','sixth':'SKILL_6'})
    @property
    def hexa(self):return SourceGroup(self,{'cores':'HEXA_CORES','stats':'HEXA_STATS'})
    @property
    def special_rings(self):return SourceGroup(self,{'exchange':'RING_EXCHANGE','reserve':'RING_RESERVE'})
    def to_view(self):
        result={'view_version':'1','sources':{s.value:{'status':self.sheet.document(s).status.value,'error_code':None if self.sheet.document(s).error is None else self.sheet.document(s).error.code,'diagnostics':list(self.sheet.document(s).diagnostics())} for s in sorted((Source(k) for k in CONTRACT['sources']),key=lambda s:s.value)}}
        for s,(name,_) in CONTRACT['sources'].items():
            if s.startswith(('UNION_','SKILL_','HEXA_','RING_')):continue
            result[name]=self._view_source(Source(s))
        for group in ('union','skills','hexa','special_rings'):result[group]=getattr(self,group).to_view()
        return result
    def to_json(self):return json.dumps(self.to_view(),ensure_ascii=False,allow_nan=False)
