"""Run with MAPLE_OPEN_API_KEY and MAPLE_CHARACTER_NAME in the environment."""
import asyncio
import os

from maple_library import (
    CharacterRequest, DocumentStatus, FieldState, MapleClient, Source,
)


def print_summary(sheet):
    equipment = sheet.document(Source.EQUIPMENT)
    if equipment.status is DocumentStatus.FETCHED:
        items = equipment.equipment()
        if items.state is FieldState.VALUE:
            for item in items.value:
                slot = item.text("item_equipment_slot")
                name = item.text("item_name")
                if slot.state is FieldState.VALUE and name.state is FieldState.VALUE:
                    print(slot.value, name.value)

    symbols = sheet.document(Source.SYMBOLS)
    if symbols.status is DocumentStatus.FETCHED:
        entries = symbols.symbols()
        if entries.state is FieldState.VALUE:
            for symbol in entries.value:
                name = symbol.text("symbol_name")
                level = symbol.number("symbol_level")
                if name.state is FieldState.VALUE and level.state is FieldState.VALUE:
                    print(name.value, level.value)


async def main():
    async with MapleClient(os.environ["MAPLE_OPEN_API_KEY"]) as client:
        sheet = await client.get_character_selected(
            CharacterRequest(nickname=os.environ["MAPLE_CHARACTER_NAME"]),
            {Source.EQUIPMENT, Source.SYMBOLS},
        )
        print_summary(sheet)


if __name__ == "__main__":
    asyncio.run(main())
