"""Deterministic raw changes with exact deltas only for declared numeric fields."""
from dataclasses import dataclass
from .raw import Kind, RawValue, MapleError, subtract_exact
from .model import Source, DocumentStatus, FieldState, read_field, number_field, escape, equipment_array

# Numeric meaning is path-bound: an unknown field named 'str' is not a stat.
SYMBOL_NUMBERS = frozenset(("symbol_level", "symbol_force", "symbol_str", "symbol_dex",
                            "symbol_int", "symbol_luk", "symbol_hp", "symbol_growth_count",
                            "symbol_require_growth_count"))
OPTION_GROUPS = frozenset(("item_base_option", "item_add_option", "item_etc_option",
                           "item_starforce_option", "item_total_option", "item_exceptional_option"))
OPTION_NUMBERS = frozenset(("str", "dex", "int", "luk", "max_hp", "max_mp", "attack_power",
                            "magic_power", "armor", "speed", "jump", "all_stat", "damage",
                            "boss_damage", "ignore_monster_armor", "max_hp_rate", "max_mp_rate"))


def numeric(source, path):
    tokens = path.split("/")[1:]
    if source is Source.SYMBOLS:
        return len(tokens) == 3 and tokens[0] == "symbol" and tokens[1].isdigit() and tokens[2] in SYMBOL_NUMBERS
    if source is Source.REPORTED_STATS:
        return len(tokens)==3 and tokens[0]=='final_stat' and tokens[1].isdigit() and tokens[2]=='stat_value'
    from .registry import specification
    pattern='/'+'/'.join('*' if t.isascii() and t.isdigit() else t for t in tokens)
    if specification(source)['fields'].get(pattern) in ('integer','number'): return True
    return (source is Source.EQUIPMENT and len(tokens) == 4 and equipment_array(tokens[0])
            and tokens[1].isdigit() and tokens[2] in OPTION_GROUPS and tokens[3] in OPTION_NUMBERS)


@dataclass(frozen=True, slots=True)
class Change:
    source: Source
    before_pointer: str
    after_pointer: str
    kind: str
    before: object
    after: object
    semantic_changed: bool
    delta: object = None


@dataclass(frozen=True, slots=True)
class SheetDiff:
    compared_documents: tuple
    skipped_documents: tuple
    changes: tuple
    diagnostics: tuple


def compare(before, after):
    if before.ocid != after.ocid:
        raise MapleError("SUBJECT_MISMATCH")
    if before.contract_version != after.contract_version:
        raise MapleError("INCOMPATIBLE_CONTRACT")
    same_rules = before.parser_rules_version == after.parser_rules_version
    diagnostics = [] if same_rules else ["PARSER_VERSION_DIFFERENCE"]
    changes, compared, skipped = [], [], []

    def walk(source, left, right, bp, ap, allow_numeric=True):
        if left == right:
            return
        if left is not None and right is not None and left.kind is right.kind is Kind.OBJECT:
            if source is Source.REPORTED_STATS and ('stat_value' in left.value or 'stat_value' in right.value):
                a,b=left.child('stat_name'),right.child('stat_name')
                allow_numeric=allow_numeric and a is not None and a.kind is Kind.STRING and bool(a.value) and a==b
            for key in sorted(left.value.keys() | right.value.keys()):
                walk(source, left.child(key), right.child(key), bp + "/" + escape(key), ap + "/" + escape(key),allow_numeric)
            return
        if left is not None and right is not None and left.kind is right.kind is Kind.ARRAY:
            key_field = ('item_equipment_slot' if source is Source.EQUIPMENT and equipment_array(ap[1:]) and ap.count('/')==1
                         else 'stat_name' if source is Source.REPORTED_STATS and ap=='/final_stat'
                         else 'preset_no' if source is Source.UNION_RAIDER and ap=='/union_raider_preset' else None)
            keyed = key_field is not None
            def slots(array):
                values = {}
                for i, item in enumerate(array.value):
                    slot = item.child(key_field)
                    if slot is None or slot.kind not in (Kind.STRING,Kind.NUMBER) or not slot.value:
                        return None, False
                    if slot.value in values:
                        return None, True
                    values[slot.value] = (i, item)
                return values, False
            if keyed:
                a, adup = slots(left)
                b, bdup = slots(right)
                if adup or bdup:
                    diagnostics.append("DUPLICATE_ENTITY_KEY:" + source.value + ":" + ap)
                if a is not None and b is not None:
                    for key in sorted(a.keys() | b.keys()):
                        ai, av = a.get(key, (None, None))
                        bi, bv = b.get(key, (None, None))
                        walk(source, av, bv, bp + "/" + str(ai) if ai is not None else bp,
                             ap + "/" + str(bi) if bi is not None else ap)
                    return
            for i in range(max(len(left.value), len(right.value))):
                walk(source, left.value[i] if i < len(left.value) else None,
                     right.value[i] if i < len(right.value) else None, bp + "/" + str(i), ap + "/" + str(i))
            return
        bfield, afield = read_field(left, bp), read_field(right, ap)
        delta = None
        semantic = True
        if allow_numeric and numeric(source, bp) and numeric(source, ap):
            bfield, afield = number_field(left, bp), number_field(right, ap)
            if same_rules and bfield.state is afield.state is FieldState.VALUE:
                delta = subtract_exact(afield.value, bfield.value)
                semantic = not delta.is_zero()
        changes.append(Change(source, bp, ap, "ADDED" if left is None else "REMOVED" if right is None else "CHANGED",
                              bfield, afield, semantic, delta))

    for source in sorted(before.documents.keys() | after.documents.keys(), key=lambda s: s.value):
        a, b = before.document(source), after.document(source)
        if a.status is not DocumentStatus.FETCHED or b.status is not DocumentStatus.FETCHED:
            skipped.append((source, a.status.value + "/" + b.status.value))
            continue
        if not a.structurally_valid or not b.structurally_valid:
            skipped.append((source, "INVALID_DOCUMENT"))
            continue
        compared.append(source)
        walk(source, a.raw, b.raw, "", "")
    # Path tokens: preset numbers sort numerically; other text uses code points.
    def token_key(token):
        prefix = "item_equipment_preset_"
        if token.startswith(prefix) and token[len(prefix):].isascii() and token[len(prefix):].isdigit():
            return (prefix, 1, int(token[len(prefix):]))
        return (token, 0, 0)
    changes.sort(key=lambda c: (c.source.value, tuple(token_key(x) for x in c.after_pointer.split("/")), c.before_pointer))
    return SheetDiff(tuple(compared), tuple(skipped), tuple(changes), tuple(sorted(set(diagnostics))))
