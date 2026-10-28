"""Synthetic baseline, not a throughput guarantee. No API calls or private data."""
import json,time,tracemalloc,platform
from maple_library import Document,CharacterSheet,Source
payload=json.dumps({'item_equipment':[{'item_equipment_slot':str(i),'item_base_option':{'str':'12345'},'potential_option_1':'STR : +12%'} for i in range(1000)]})
tracemalloc.start();start=time.perf_counter()
for _ in range(10):
 doc=Document.parse(Source.EQUIPMENT,payload)
 options=doc.decoded_options()
 sheet=CharacterSheet('synthetic',{Source.EQUIPMENT:doc})
 assert not sheet.compare_to(sheet).changes
elapsed=time.perf_counter()-start
print(json.dumps({'python':platform.python_version(),'iterations':10,'entities_per_iteration':1000,'utf8_bytes':len(payload.encode()),'seconds':round(elapsed,4),'tracemalloc_peak_bytes':tracemalloc.get_traced_memory()[1],'note':'synthetic parse+options+self-diff; no HTTP; no SLA'}))
