"""Bounded opt-in public-ranking sample audit; never writes character identities/raw sheets."""
import argparse,asyncio,json,collections,re
from pathlib import Path
from datetime import date
import httpx
from live_smoke import load_key
from maple_library import MapleClient,CharacterRequest,Source,DocumentStatus,ClientConfig,RateLimiter

async def run(args):
 key=load_key(args.env_file);limiter=RateLimiter();vocabulary=collections.Counter();shapes=collections.defaultdict(set);reports=[]
 async with httpx.AsyncClient(trust_env=False,timeout=20) as http:
  await limiter.acquire()
  response=await http.get('https://open.api.nexon.com/maplestory/v1/ranking/overall',params={'date':args.ranking_date,'world_name':'스카니아','page':1},headers={'x-nxopen-api-key':key})
  if response.status_code!=200:raise RuntimeError('RANKING_FAILED:'+str(response.status_code))
  selected={}
  for row in response.json().get('ranking',[]):
   job=row.get('sub_class_name') or row.get('class_name')
   if job and job not in selected:selected[job]=row['character_name']
   if len(selected)==args.samples:break
  if len(selected)<args.samples:raise RuntimeError('INSUFFICIENT_DISTINCT_JOBS')
 async with MapleClient(key,limiter=limiter,config=ClientConfig(retries=0)) as client:
  for job,name in selected.items():
   sheet=await client.get_character(CharacterRequest(nickname=name,allow_partial=True))
   report={'job':job,'documents':{},'historical':{}}
   for source,doc in sheet.documents.items():
    options=doc.decoded_options();counts=collections.Counter(o.option.status for o in options)
    report['documents'][source.value]={'status':doc.status.value,'options':dict(counts),'diagnostics':len(doc.diagnostics())}
    for o in options:vocabulary[(source.value,o.option.raw_text)]+=1
    if doc.status is DocumentStatus.FETCHED:
     def walk(v,path):
      shapes[(source.value,path)].add(v.kind.value)
      if v.kind.value=='OBJECT':
       for k,child in v.value.items():walk(child,path+'/'+k)
      elif v.kind.value=='ARRAY':
       for child in v.value:walk(child,path+'/*')
     walk(doc.raw,'')
   historical=await client.get_character_selected(CharacterRequest(ocid=sheet.ocid,on_date=date.fromisoformat(args.history_date),allow_partial=True),{Source.EQUIPMENT,Source.SYMBOLS,Source.SKILL_6})
   report['historical']={s.value:d.status.value for s,d in historical.documents.items()}
   report['history_diagnostics']=list(historical.fetch_diagnostics)
   reports.append(report)
   print(json.dumps({'job':job,'fetched':sum(d.status is DocumentStatus.FETCHED for d in sheet.documents.values()),'history':report['historical']},ensure_ascii=False),flush=True)
 result={'ranking_date':args.ranking_date,'history_date':args.history_date,'samples':reports,'maximum_calls':1+args.samples*30}
 Path(args.report).write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
 audit={'vocabulary':[{'source':s,'text':t,'count':n} for (s,t),n in vocabulary.most_common()], 'shapes':[{'source':s,'path':p,'types':sorted(types)} for (s,p),types in sorted(shapes.items())]}
 Path(args.audit).write_text(json.dumps(audit,ensure_ascii=False,indent=2)+'\n')

if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--env-file',required=True);p.add_argument('--ranking-date',required=True);p.add_argument('--history-date',required=True);p.add_argument('--samples',type=int,choices=range(1,5),default=4);p.add_argument('--report',required=True);p.add_argument('--audit',required=True)
 asyncio.run(run(p.parse_args()))
