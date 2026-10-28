"""Run with MAPLE_OPEN_API_KEY and MAPLE_CHARACTER_NAME in the environment."""
import asyncio
import os
from maple_library import MapleClient, Source


def print_summary(character):
    for item in character.equipment.items:
        print(item.slot, item.name)
    for symbol in character.symbols.items:
        print(symbol.name, symbol.level)


async def main():
    async with MapleClient(os.environ['MAPLE_OPEN_API_KEY']) as maple:
        character = await maple.character(
            os.environ['MAPLE_CHARACTER_NAME'],
            sources=[Source.EQUIPMENT, Source.SYMBOLS, Source.UNION],
        )
        print_summary(character)
        print(character.union.basic.level)
        # A web handler can return this dict directly as its JSON response.
        view = character.to_view()
        print(view['equipment']['items'])


if __name__ == '__main__':
    asyncio.run(main())
