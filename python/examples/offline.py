"""Run against an installed wheel; no key, network, or repository fixtures needed."""
from decimal import Decimal
from maple_library import CharacterSheet, Document, Source

before = CharacterSheet('synthetic-example', {
    Source.SYMBOLS: Document.parse(Source.SYMBOLS, '{"symbol":[{"symbol_name":"합성 심볼","symbol_str":"10","symbol_dex":"20"}]}')
})
after = CharacterSheet('synthetic-example', {
    Source.SYMBOLS: Document.parse(Source.SYMBOLS, '{"symbol":[{"symbol_name":"합성 심볼","symbol_str":"10","symbol_dex":"25"}]}')
})
assert before.document(Source.SYMBOLS).symbols().value[0].number('symbol_str').value == Decimal('10')
changes = before.compare_to(after).changes
assert len(changes) == 1 and changes[0].delta == Decimal('5')
print('symbol_dex: +5 (reported data; no stat aggregation)')

character = after.as_character()
symbol = character.symbols.items[0]
assert symbol.stats.dex == Decimal("25")
assert symbol.to_view()["stat_rows"][1]["text"] == "DEX +25"
print(symbol.to_json())
