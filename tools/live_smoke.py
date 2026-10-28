#!/usr/bin/env python3
"""Opt-in smoke check. Loads a local key without copying it into project files."""
import argparse
import asyncio
import json
from pathlib import Path
from datetime import date
from maple_library import MapleClient, CharacterRequest, DocumentStatus, Source, MapleError


def load_key(path):
    for line in Path(path).read_text().splitlines():
        name, sep, value = line.removeprefix('export ').partition('=')
        if sep and name.strip() == 'MAPLE_OPEN_API_KEY':
            key = value.strip().strip('\"\'')
            if key:return key
    raise ValueError('MAPLE_OPEN_API_KEY not found')


async def run(args):
    key = load_key(args.env_file)
    try:
        async with MapleClient(key) as client:
            sheet = await client.get_character(CharacterRequest(nickname=args.nickname, allow_partial=True))
            status = {s.value: {'status':d.status.value, 'attempts':d.attempts,
                       'error':d.error.code if d.error else None,
                       'structure_valid':d.structurally_valid,
                       'schema_diagnostics':len(d.diagnostics()),
                       'options':len(d.decoded_options()) if d.status is DocumentStatus.FETCHED else 0}
                      for s,d in sheet.documents.items()}
            result = {'documents':status,'quality':dict(sheet.quality())}
            if args.date:
                historic = await client.get_character_selected(CharacterRequest(ocid=sheet.ocid,on_date=date.fromisoformat(args.date)), {Source.SYMBOLS})
                result['historical']={'sources':[s.value for s in historic.documents], 'diagnostics':list(historic.fetch_diagnostics), 'quality':dict(historic.quality())}
            text=json.dumps(result,ensure_ascii=False,indent=2)
            if key in text:raise ValueError('Refusing secret-bearing output')
            if args.report:Path(args.report).write_text(text+'\n')
            print(text)
    except MapleError as error:
        print(json.dumps({'error':error.code}))
        raise SystemExit(1) from None


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--env-file',required=True)
    parser.add_argument('--nickname',required=True)
    parser.add_argument('--date')
    parser.add_argument('--report')
    asyncio.run(run(parser.parse_args()))
