"""Offline document model: symbol and item-equipment views, no game calculations."""
from dataclasses import dataclass, field
from enum import Enum
from types import MappingProxyType
import re
from .raw import RawValue, Kind, MapleError, DECIMAL, checked_decimal, parse_json


class FieldState(str, Enum):
    MISSING = "MISSING"
    NULL = "NULL"
    VALUE = "VALUE"
    INVALID = "INVALID"


@dataclass(frozen=True, slots=True)
class FieldValue:
    state: FieldState
    value: object = None
    raw: RawValue | None = None
    pointer: str = ""


def escape(token: str) -> str:
    return token.replace("~", "~0").replace("/", "~1")


def read_field(raw, pointer, kind=None):
    if raw is None:
        return FieldValue(FieldState.MISSING, pointer=pointer)
    if raw.kind is Kind.NULL:
        return FieldValue(FieldState.NULL, raw=raw, pointer=pointer)
    if kind is not None and raw.kind is not kind:
        return FieldValue(FieldState.INVALID, raw=raw, pointer=pointer)
    return FieldValue(FieldState.VALUE, raw.value, raw, pointer)


def number_field(raw, pointer):
    result = read_field(raw, pointer)
    if result.state is not FieldState.VALUE:
        return result
    if raw.kind not in (Kind.NUMBER, Kind.STRING):
        return FieldValue(FieldState.INVALID, raw=raw, pointer=pointer)
    if raw.kind is Kind.STRING and not DECIMAL.fullmatch(raw.value):
        return FieldValue(FieldState.INVALID, raw=raw, pointer=pointer)
    try:
        return FieldValue(FieldState.VALUE, checked_decimal(raw.value), raw, pointer)
    except (ValueError, ArithmeticError):
        return FieldValue(FieldState.INVALID, raw=raw, pointer=pointer)


@dataclass(frozen=True, slots=True)
class EntityView:
    raw: RawValue
    pointer: str

    def field(self, name: str) -> FieldValue:
        path = self.pointer + "/" + escape(name)
        if self.raw.kind is not Kind.OBJECT:
            return FieldValue(FieldState.INVALID, raw=self.raw, pointer=path)
        return read_field(self.raw.child(name), path)

    def text(self, name: str) -> FieldValue:
        value = self.field(name)
        if value.state is not FieldState.VALUE:
            return value
        return read_field(value.raw, value.pointer, Kind.STRING)

    def number(self, name: str) -> FieldValue:
        value = self.field(name)
        if value.state is not FieldState.VALUE:
            return value
        return number_field(value.raw, value.pointer)

    def timestamp(self, name: str) -> FieldValue:
        from datetime import datetime
        value=self.text(name)
        if value.state is not FieldState.VALUE: return value
        try:
            parsed=datetime.fromisoformat(value.value)
            if parsed.tzinfo is None: raise ValueError()
            return FieldValue(FieldState.VALUE,parsed,value.raw,value.pointer)
        except ValueError:
            return FieldValue(FieldState.INVALID,raw=value.raw,pointer=value.pointer)

    def object(self, name: str) -> FieldValue:
        value = self.field(name)
        if value.state is not FieldState.VALUE:
            return value
        value = read_field(value.raw, value.pointer, Kind.OBJECT)
        return FieldValue(value.state, EntityView(value.raw, value.pointer) if value.state is FieldState.VALUE else None,
                          value.raw, value.pointer)

    def entities(self, name: str) -> FieldValue:
        value = self.field(name)
        if value.state is not FieldState.VALUE:
            return value
        value = read_field(value.raw, value.pointer, Kind.ARRAY)
        if value.state is not FieldState.VALUE:
            return value
        return FieldValue(FieldState.VALUE, tuple(EntityView(v, value.pointer + "/" + str(i))
                                                   for i, v in enumerate(value.value)), value.raw, value.pointer)


def equipment_array(name: str) -> bool:
    return name == "item_equipment" or re.fullmatch(r"item_equipment_preset_[1-9][0-9]*", name, re.ASCII) is not None


class Source(str, Enum):
    BASIC = "BASIC"
    REPORTED_STATS = "REPORTED_STATS"
    HYPER_STATS = "HYPER_STATS"
    PROPENSITY = "PROPENSITY"
    ABILITIES = "ABILITIES"
    EQUIPMENT = "EQUIPMENT"
    CASH = "CASH"
    SYMBOLS = "SYMBOLS"
    SET_EFFECTS = "SET_EFFECTS"
    ANDROID = "ANDROID"
    PETS = "PETS"
    SKILL_0 = "SKILL_0"
    SKILL_HYPER_PASSIVE = "SKILL_HYPER_PASSIVE"
    SKILL_HYPER_ACTIVE = "SKILL_HYPER_ACTIVE"
    SKILL_5 = "SKILL_5"
    SKILL_6 = "SKILL_6"
    LINK_SKILLS = "LINK_SKILLS"
    V_MATRIX = "V_MATRIX"
    HEXA_CORES = "HEXA_CORES"
    HEXA_STATS = "HEXA_STATS"
    OTHER_STATS = "OTHER_STATS"
    RING_EXCHANGE = "RING_EXCHANGE"
    RING_RESERVE = "RING_RESERVE"
    UNION_BASIC = "UNION_BASIC"
    UNION_RAIDER = "UNION_RAIDER"
    UNION_ARTIFACT = "UNION_ARTIFACT"
    UNION_CHAMPION = "UNION_CHAMPION"
    SKILLS = "SKILLS"
    UNION = "UNION"


class DocumentStatus(str, Enum):
    FETCHED = "FETCHED"
    NOT_REQUESTED = "NOT_REQUESTED"
    FETCH_FAILED = "FETCH_FAILED"
    UNSUPPORTED = "UNSUPPORTED"


@dataclass(frozen=True, slots=True)
class Document:
    source: Source
    status: DocumentStatus
    raw: RawValue | None = None
    fetched_at: object = None
    error: object = None
    attempts: int = 0

    def __post_init__(self):
        if not isinstance(self.source, Source) or not isinstance(self.status, DocumentStatus):
            raise MapleError("INVALID_REQUEST")
        if self.source in (Source.SKILLS, Source.UNION):
            raise MapleError("INVALID_REQUEST")
        if self.status is DocumentStatus.FETCHED:
            if self.raw is None or self.raw.kind is not Kind.OBJECT:
                raise MapleError("INVALID_DOCUMENT")
        elif self.raw is not None and (self.status is not DocumentStatus.FETCH_FAILED or self.raw.kind is not Kind.OBJECT):
            raise MapleError("INVALID_DOCUMENT")

    @classmethod
    def parse(cls, source: Source, text: str):
        return cls(source, DocumentStatus.FETCHED, parse_json(text))

    @property
    def view(self):
        if self.status is not DocumentStatus.FETCHED:
            raise MapleError("DOCUMENT_UNAVAILABLE")
        return EntityView(self.raw, "")

    def as_model(self):
        from .presentation import CONTRACT
        from . import friendly
        return getattr(friendly, CONTRACT["sources"][self.source.value][1])(self.view)

    def symbols(self):
        if self.source is not Source.SYMBOLS:
            raise MapleError("INVALID_REQUEST")
        return self.view.entities("symbol")

    def equipment(self, preset: int | None = None):
        if self.source is not Source.EQUIPMENT or (preset is not None and (type(preset) is not int or preset < 1 or preset > 2147483647)):
            raise MapleError("INVALID_REQUEST")
        return self.view.entities("item_equipment" if preset is None else f"item_equipment_preset_{preset}")

    def current_preset_number(self):
        key='use_preset_no' if self.source in (Source.HYPER_STATS,Source.UNION_RAIDER) else 'preset_no'
        result = self.view.number(key)
        if result.state is FieldState.VALUE:
            value = result.value
            if value != value.to_integral_value() or not 1 <= value <= 2147483647:
                return FieldValue(FieldState.INVALID, raw=result.raw, pointer=result.pointer)
            return FieldValue(FieldState.VALUE, int(value), result.raw, result.pointer)
        return result

    def collection(self, name: str):
        return self.view.entities(name)

    def primary(self):
        return self.view

    def presets(self):
        from .registry import preset_views
        return preset_views(self)

    def decoded_options(self):
        from .options import document_options
        return document_options(self)

    def diagnostics(self):
        from .registry import diagnostics
        return diagnostics(self)

    @property
    def structurally_valid(self):
        if self.status is not DocumentStatus.FETCHED:
            return False
        from .registry import specification
        types = specification(self.source)["fields"]
        names = [k for k in self.raw.value if types.get("/" + k) == "array" or (self.source is Source.EQUIPMENT and equipment_array(k))]
        for name in names:
            value = self.raw.child(name)
            if value is None or value.kind is Kind.NULL:
                continue
            if value.kind is not Kind.ARRAY:
                return False
            if types.get("/" + name + "/*") == "object" and any(v.kind is not Kind.OBJECT for v in value.value):
                return False
        return True


@dataclass(frozen=True, slots=True)
class CharacterSheet:
    ocid: str = field(repr=False)
    documents: object
    contract_version: str = "0.1"
    parser_rules_version: str = "0.2"
    requested_sources: frozenset = field(default_factory=frozenset)
    query_time: object = None
    fetch_diagnostics: tuple = ()

    def __post_init__(self):
        if not isinstance(self.ocid, str) or not self.ocid.strip():
            raise MapleError("INVALID_REQUEST")
        if not isinstance(self.contract_version, str) or not self.contract_version.strip() or not isinstance(self.parser_rules_version, str) or not self.parser_rules_version.strip():
            raise MapleError("INVALID_REQUEST")
        docs = dict(self.documents)
        if any(not isinstance(k, Source) or not isinstance(v, Document) or v.source is not k for k, v in docs.items()):
            raise MapleError("INVALID_REQUEST")
        object.__setattr__(self, "documents", MappingProxyType(docs))
        object.__setattr__(self, "requested_sources", frozenset(self.requested_sources or docs))
        object.__setattr__(self, "fetch_diagnostics", tuple(self.fetch_diagnostics))

    def document(self, source: Source):
        return self.documents.get(source, Document(source, DocumentStatus.NOT_REQUESTED))

    def as_character(self):
        from .friendly import Character
        return Character(self)

    def compare_to(self, after):
        from .diff import compare
        return compare(self, after)

    def quality(self):
        from .registry import expand_sources, all_sources
        expanded = expand_sources(self.requested_sources) if self.requested_sources else frozenset()
        docs = [self.document(s) for s in expanded]
        return MappingProxyType({
            "requested_complete": all(d.status is DocumentStatus.FETCHED for d in docs),
            "structure_valid": all(d.structurally_valid for d in docs),
            "all_sources": expanded == all_sources(),
            "interpretation_complete": all(d.status is DocumentStatus.FETCHED for d in docs) and all(o.option.status == "KNOWN" for d in docs if d.status is DocumentStatus.FETCHED for o in d.decoded_options()),
        })
