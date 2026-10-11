"""Offline isolated rehearsal: review -> quarantine old unreferenced files -> restore.

No permanent deletion, quota changes, metadata writes, production paths or running DBs.
References include logically removed records and ALL verification versions.
"""
import argparse, datetime, getpass, hashlib, json, os, pathlib, re, subprocess, sys, time, uuid

WORKSPACE=pathlib.Path(__file__).resolve().parents[1]
TARGET=(WORKSPACE/'backend'/'target').resolve()
MIN_AGE_SECONDS=7*86400
QUARANTINE_MIN_DAYS=30  # manual review/recovery window; this tool never purges
REF_SQL="""SELECT DISTINCT key FROM (
 SELECT storage_key AS key FROM learning_files
 UNION ALL SELECT storage_key FROM support_evidence
 UNION ALL SELECT storage_key FROM consultation_files
 UNION ALL SELECT id_document_key FROM verification_cases
 UNION ALL SELECT degree_cert_key FROM verification_cases
) r WHERE key IS NOT NULL AND key<>'' ORDER BY key;"""

def sql(database,statement):
    result=subprocess.run(['docker','exec','-i','deploy-postgres-1','psql','-U','nurture','-d',database,'-X','-At','-v','ON_ERROR_STOP=1'],input=statement,encoding='utf-8',capture_output=True,check=True)
    return result.stdout.strip()

def write_json(path,data):
    # Atomic replacement for own manifest only, after validating its parent path.
    temp=path.with_suffix('.tmp')
    with temp.open('w',encoding='utf-8') as stream:
        json.dump(data,stream,ensure_ascii=False,indent=2);stream.flush();os.fsync(stream.fileno())
    os.replace(temp,path)

def safe(base,key):
    p=pathlib.PurePosixPath(key)
    if p.is_absolute() or not p.parts or any(x in ('..','.') for x in p.parts) or '\\' in key or ':' in key:
        raise ValueError('Unsafe storage key')
    path=base.joinpath(*p.parts)
    if not path.resolve().is_relative_to(base):raise ValueError('Path leaves isolated storage')
    for part in (path,*path.parents):
        if part==base.parent:break
        if part.is_symlink() or (hasattr(part,'is_junction') and part.is_junction()):raise ValueError('Symlink/junction refused')
    return path

def fingerprint(path):
    stat=path.stat()
    return {'size':stat.st_size,'mtimeNs':stat.st_mtime_ns,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()}

def context(database,root):
    if not re.fullmatch(r'nurture_gap_acceptance_file_retention_[a-z0-9_]+',database):raise ValueError('Only named retention rehearsal databases allowed')
    raw=pathlib.Path(root).absolute()
    if raw.is_symlink() or (hasattr(raw,'is_junction') and raw.is_junction()):raise ValueError('Scope link refused')
    root=raw.resolve()
    if root.parent!=TARGET or not root.name.startswith('file-retention-rehearsal-'):raise ValueError('Only backend/target/file-retention-rehearsal-* scope allowed')
    safe(TARGET,root.name)
    scope=json.loads(safe(root,'.retention-scope.json').read_text(encoding='utf-8'))
    if scope!={'database':database,'root':str(root),'offline':True}:raise ValueError('Scope marker mismatch')
    version=int(sql(database,"SELECT max(version::int) FROM flyway_schema_history WHERE success AND version ~ '^[0-9]+$';"))
    if version<20:raise ValueError('Reference schema is incomplete')
    # Do not run while uploads/transactions could create references after the check.
    if int(sql(database,"SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND pid<>pg_backend_pid();")):
        raise ValueError('Database has other active connections; rehearsal must remain offline')
    refs=set(sql(database,REF_SQL).splitlines())
    for key in refs:safe(root,key)
    return root,refs

def audit(root,action,batch,details):
    event={'at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'actor':getpass.getuser(),'action':action,'batchId':batch,'details':details}
    p=safe(root,'.retention/audit.jsonl');p.parent.mkdir(exist_ok=True)
    with p.open('a',encoding='utf-8') as stream:
        stream.write(json.dumps(event,ensure_ascii=False)+'\n');stream.flush();os.fsync(stream.fileno())

def inventory(database,root):
    root,refs=context(database,root);now=time.time();candidates=[];protected=[];recent=[]
    for parent,dirs,files in os.walk(root,followlinks=False):
        dirs[:]=[d for d in dirs if d!='.retention']
        for directory in dirs:safe(root,pathlib.Path(parent,directory).relative_to(root).as_posix())
        for name in files:
            if pathlib.Path(parent)==root and name=='.retention-scope.json':continue
            path=pathlib.Path(parent,name);key=path.relative_to(root).as_posix();path=safe(root,key)
            if key in refs:protected.append(key)
            elif now-path.stat().st_mtime<MIN_AGE_SECONDS:recent.append(key)
            else:candidates.append({'key':key,**fingerprint(path)})
    missing=sorted(key for key in refs if not safe(root,key).is_file())
    batch=str(uuid.uuid4());plan={'policyVersion':1,'batchId':batch,'database':database,'root':str(root),'createdAt':now,'minimumOrphanDays':7,'quarantineMinimumDays':30,'candidates':sorted(candidates,key=lambda x:x['key']),'protected':sorted(protected),'recent':sorted(recent),'missingReferenced':missing}
    file=safe(root,'.retention/'+batch+'/plan.json');file.parent.mkdir(parents=True,exist_ok=False);write_json(file,plan)
    audit(root,'INVENTORY',batch,{'candidateCount':len(candidates),'protectedCount':len(protected),'missingReferenced':len(missing)})
    return plan

def load_plan(database,root,batch):
    root,refs=context(database,root)
    if str(uuid.UUID(batch))!=batch:raise ValueError('Batch must be canonical UUID')
    folder=safe(root,'.retention/'+batch);plan=json.loads(safe(root,'.retention/'+batch+'/plan.json').read_text(encoding='utf-8'))
    if plan['policyVersion']!=1 or plan['batchId']!=batch or plan['database']!=database or plan['root']!=str(root):raise ValueError('Plan scope mismatch')
    if len({r['key'] for r in plan['candidates']})!=len(plan['candidates']):raise ValueError('Duplicate candidates refused')
    for row in plan['candidates']:
        if row['key'].startswith('.') or not re.fullmatch(r'[a-zA-Z0-9_-]+/[0-9a-f-]{36}\.[a-z0-9]+(?:\.tmp)?',row['key']):raise ValueError('Unrecognized storage path refused')
        safe(root,row['key']);safe(folder,'files/'+row['key'])
    return root,refs,folder,plan

def quarantine(database,root,batch):
    root,refs,folder,plan=load_plan(database,root,batch)
    if plan['missingReferenced']:raise ValueError('Resolve missing referenced files before rehearsal')
    if time.time()-plan['createdAt']>3600:raise ValueError('Plan expired; inventory again')
    state_path=safe(folder,'state.json');state=json.loads(state_path.read_text(encoding='utf-8')) if state_path.exists() else {'moved':[],'restored':False}
    if state['restored']:return {'status':'already-restored','moved':len(state['moved'])}
    # Validate whole batch before the first move; stale/tampered plans have no partial effect.
    for row in plan['candidates']:
        source=safe(root,row['key']);dest=safe(folder,'files/'+row['key'])
        if row['key'] in refs:raise ValueError('New database reference blocks quarantine')
        if not source.exists() and dest.is_file() and fingerprint(dest)=={k:row[k] for k in ('size','mtimeNs','sha256')}:continue
        if dest.exists() or not source.is_file() or fingerprint(source)!={k:row[k] for k in ('size','mtimeNs','sha256')} or time.time()-source.stat().st_mtime<MIN_AGE_SECONDS:raise ValueError('Candidate changed or destination conflict')
    for row in plan['candidates']:
        # Recheck offline scope and all references immediately before each reversible move.
        _,current_refs=context(database,root)
        if row['key'] in current_refs:raise ValueError('Reference appeared; stop and restore completed moves')
        source=safe(root,row['key']);dest=safe(folder,'files/'+row['key'])
        if source.exists():
            if fingerprint(source)!={k:row[k] for k in ('size','mtimeNs','sha256')}:raise ValueError('Candidate changed during batch')
            dest.parent.mkdir(parents=True,exist_ok=True)
            audit(root,'QUARANTINE_INTENT',batch,{'key':row['key'],'sha256':row['sha256']})
            os.rename(source,dest)
        if row['key'] not in state['moved']:state['moved'].append(row['key'])
        state['quarantinedAt']=state.get('quarantinedAt',time.time());write_json(state_path,state)
        audit(root,'QUARANTINE',batch,{'key':row['key'],'sha256':row['sha256']})
    return {'status':'quarantined','moved':len(state['moved']),'permanentlyDeleted':0}

def restore(database,root,batch):
    root,refs,folder,plan=load_plan(database,root,batch);state_path=safe(folder,'state.json')
    state=json.loads(state_path.read_text(encoding='utf-8')) if state_path.exists() else {'moved':[],'restored':False}
    restored=0
    for row in plan['candidates']:
        source=safe(folder,'files/'+row['key']);dest=safe(root,row['key'])
        if source.exists():
            if not source.is_file() or fingerprint(source)!={k:row[k] for k in ('size','mtimeNs','sha256')}:raise ValueError('Quarantine content changed')
            if dest.exists():raise ValueError('Restore destination conflict; no overwrite')
            dest.parent.mkdir(parents=True,exist_ok=True);audit(root,'RESTORE_INTENT',batch,{'key':row['key']});os.rename(source,dest);restored+=1
        elif row['key'] in state['moved'] and (not dest.is_file() or fingerprint(dest)!={k:row[k] for k in ('size','mtimeNs','sha256')}):raise ValueError('Previously moved file is missing or changed')
    state['restored']=True;write_json(state_path,state);audit(root,'RESTORE',batch,{'restored':restored})
    return {'status':'restored','restored':restored,'permanentlyDeleted':0}

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('action',choices=['inventory','quarantine','restore']);p.add_argument('--database',required=True);p.add_argument('--root',required=True);p.add_argument('--batch')
    a=p.parse_args()
    if a.action!='inventory' and not a.batch:p.error('--batch required')
    result=inventory(a.database,a.root) if a.action=='inventory' else globals()[a.action](a.database,a.root,a.batch)
    print(json.dumps(result,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
