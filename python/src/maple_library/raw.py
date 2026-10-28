"""Immutable JSON values. Numeric tokens are retained without float conversion."""
from dataclasses import dataclass
from decimal import Decimal
from enum import Enum
from types import MappingProxyType
import json
import re


class MapleError(ValueError):
    def __init__(self, code: str):
        self.code = code
        super().__init__(code)


class Kind(str, Enum):
    OBJECT = "OBJECT"
    ARRAY = "ARRAY"
    STRING = "STRING"
    NUMBER = "NUMBER"
    BOOLEAN = "BOOLEAN"
    NULL = "NULL"


NUMBER = re.compile(r"-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?\Z", re.ASCII)
DECIMAL = re.compile(r"[+-]?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?\Z", re.ASCII)


def checked_decimal(token: str) -> Decimal:
    if len(token) > 1024:
        raise MapleError("RESOURCE_LIMIT_EXCEEDED")
    exponent = re.split("[eE]", token)
    if len(exponent) == 2 and abs(int(exponent[1])) > 5120:
        raise MapleError("RESOURCE_LIMIT_EXCEEDED")
    value = Decimal(token)
    # Bound expansion too: a short exponent must not allocate unbounded output.
    if not value.is_finite() or abs(value.as_tuple().exponent) > 4096:
        raise MapleError("RESOURCE_LIMIT_EXCEEDED")
    return value


@dataclass(frozen=True, slots=True)
class RawValue:
    kind: Kind
    value: object = None

    def __post_init__(self):
        if self.kind is Kind.OBJECT:
            data = dict(self.value)
            if any(not isinstance(k, str) or not isinstance(v, RawValue) for k, v in data.items()):
                raise MapleError("INVALID_JSON")
            object.__setattr__(self, "value", MappingProxyType(data))
        elif self.kind is Kind.ARRAY:
            data = tuple(self.value)
            if any(not isinstance(v, RawValue) for v in data):
                raise MapleError("INVALID_JSON")
            object.__setattr__(self, "value", data)
        elif self.kind in (Kind.STRING, Kind.NUMBER):
            if not isinstance(self.value, str):
                raise MapleError("INVALID_JSON")
            if self.kind is Kind.NUMBER:
                if not NUMBER.fullmatch(self.value):
                    raise MapleError("INVALID_JSON")
                checked_decimal(self.value)
        elif self.kind is Kind.BOOLEAN:
            if type(self.value) is not bool:
                raise MapleError("INVALID_JSON")
        elif self.kind is not Kind.NULL or self.value is not None:
            raise MapleError("INVALID_JSON")

    def child(self, name: str):
        return self.value.get(name) if self.kind is Kind.OBJECT else None


def parse_json(text: str) -> RawValue:
    if not isinstance(text, str):
        raise MapleError("INVALID_JSON")
    try:
        if len(text.encode("utf-8")) > 16 * 1024 * 1024:
            raise MapleError("RESOURCE_LIMIT_EXCEEDED")
        # Check nesting before json.loads, whose recursion limit varies by runtime.
        depth = 0
        quoted = escaped = False
        for c in text:
            if quoted:
                if escaped:
                    escaped = False
                elif c == "\\":
                    escaped = True
                elif c == '"':
                    quoted = False
            elif c == '"':
                quoted = True
            elif c in "[{":
                depth += 1
                if depth > 128:
                    raise MapleError("RESOURCE_LIMIT_EXCEEDED")
            elif c in "]}":
                depth -= 1

        def pairs(items):
            values = {}
            for key, value in items:
                if key in values:
                    raise MapleError("INVALID_JSON")
                values[key] = freeze(value)
            return RawValue(Kind.OBJECT, values)

        def freeze(value):
            if isinstance(value, RawValue):
                return value
            if value is None:
                return RawValue(Kind.NULL)
            if isinstance(value, list):
                return RawValue(Kind.ARRAY, tuple(freeze(v) for v in value))
            if isinstance(value, str):
                return RawValue(Kind.STRING, value)
            if type(value) is bool:
                return RawValue(Kind.BOOLEAN, value)
            raise MapleError("INVALID_JSON")

        def invalid_constant(_):
            raise MapleError("INVALID_JSON")

        return freeze(json.loads(text, object_pairs_hook=pairs,
                                 parse_int=lambda s: RawValue(Kind.NUMBER, s),
                                 parse_float=lambda s: RawValue(Kind.NUMBER, s),
                                 parse_constant=invalid_constant))
    except (UnicodeError, json.JSONDecodeError, RecursionError) as exc:
        raise MapleError("INVALID_JSON") from None


def decimal_text(value: Decimal) -> str:
    text = format(value, "f")
    if "." in text:
        text = text.rstrip("0").rstrip(".")
    return "0" if value.is_zero() else text


def subtract_exact(after: Decimal, before: Decimal) -> Decimal:
    # Decimal arithmetic must not inherit the caller's precision/rounding context.
    def parts(value):
        sign, digits, exponent = value.as_tuple()
        coefficient = 0
        for digit in digits:
            coefficient = coefficient * 10 + digit
        return -coefficient if sign else coefficient, exponent
    a, ae = parts(after)
    b, be = parts(before)
    scale = min(ae, be)
    result = a * 10 ** (ae - scale) - b * 10 ** (be - scale)
    # Conversion via digits avoids Python's decimal-string integer length limit.
    digits = []
    magnitude = abs(result)
    while magnitude:
        magnitude, digit = divmod(magnitude, 10)
        digits.append(digit)
    return Decimal((int(result < 0), tuple(reversed(digits)) or (0,), scale))
