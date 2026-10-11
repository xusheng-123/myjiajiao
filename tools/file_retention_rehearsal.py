"""Create a dedicated offline DB/storage COPY; prove quarantine and exact restore."""
import datetime, hashlib, json, os, pathlib, re, shutil, subprocess, sys, time, uuid
import file_retention as retention
sys.stdout.reconfigure(encoding='utf-8')

def rejected(action,label):
    try:action()
    except (ValueError,FileNotFoundError):print('PASS: '+label,flush=True);return
    raise AssertionError(label+' was not rejected')

def junction_check(database,root):
    root,_=retention.context(database,root)
    outside=retention.TARGET/('retention-link-sentinel-'+str(uuid.uuid4()))
    assert outside.parent==retention.TARGET;outside.mkdir()
    sentinel=outside/'sentinel.txt';sentinel.write_bytes(b'Outside the rehearsal storage; never move or delete')
    link=root/'blocked-junction'
    assert not link.exists()
    if os.name=='nt':
        env=dict(os.environ,RETENTION_TEST_LINK=str(link),RETENTION_TEST_TARGET=str(outside))
        subprocess.run(['powershell','-NoProfile','-Command','-'],input='New-Item -ItemType Junction -Path $env:RETENTION_TEST_LINK -Target $env:RETENTION_TEST_TARGET | Out-Null\n',encoding='utf-8',env=env,capture_output=True,check=True)
        assert link.is_junction()
    else:link.symlink_to(outside,target_is_directory=True)
    try:rejected(lambda:retention.inventory(database,root),'actual link/junction traversal refused')
    finally:
        # Remove only the verified link entry, without recursion; sentinel target stays intact.
        if os.name=='nt':
            assert link.is_junction() and link.resolve()==outside.resolve()
            link.rmdir()  # Windows RemoveDirectory removes this verified junction entry only.
        else:link.unlink()
    assert sentinel.read_bytes()==b'Outside the rehearsal storage; never move or delete'
    retention.audit(root,'LINK_GUARD_CHECK',str(uuid.uuid4()),{'targetUnchanged':True})

def main():
    stamp=datetime.datetime.now().strftime('%Y%m%d_%H%M%S')
    database='nurture_gap_acceptance_file_retention_'+stamp
    root=retention.TARGET/('file-retention-rehearsal-'+stamp)
    assert not root.exists()
    # SQL identifier is entirely generated; never caller-selected or production.
    assert re.fullmatch(r'[a-z0-9_]+',database)
    retention.sql('postgres',f'CREATE DATABASE {database} WITH TEMPLATE nurture_test OWNER nurture;')
    shutil.copytree(retention.TARGET/'it-files',root,symlinks=True)
    retention.write_json(root/'.retention-scope.json',{'database':database,'root':str(root.resolve()),'offline':True})
    refs=set(retention.sql(database,retention.REF_SQL).splitlines());assert refs
    before={key:hashlib.sha256(retention.safe(root,key).read_bytes()).hexdigest() for key in refs}
    # Age protected examples as well as the orphan: age alone must NEVER make references eligible.
    old=time.time()-14*86400
    for key in refs:os.utime(retention.safe(root,key),(old,old))
    old_key='learning/'+str(uuid.uuid4())+'.pdf';new_key='support/'+str(uuid.uuid4())+'.pdf'
    for key in (old_key,new_key):
        p=retention.safe(root,key);p.parent.mkdir(exist_ok=True);p.write_bytes(b'%PDF-1.4\nSynthetic orphan\n%%EOF')
    os.utime(retention.safe(root,old_key),(old,old))
    # Existing test orphan leftovers are also valid targets, but no referenced file is a candidate.
    plan=retention.inventory(database,root);batch=plan['batchId']
    assert old_key in {r['key'] for r in plan['candidates']} and new_key in plan['recent']
    assert not refs.intersection(r['key'] for r in plan['candidates']) and not plan['missingReferenced']
    print(f'PASS: inventory protects {len(refs)} referenced files (including removed); recent orphan excluded',flush=True)
    rejected(lambda:retention.context('nurture',root),'production DB refused')
    rejected(lambda:retention.context(database,retention.TARGET/'it-files'),'live/unmarked storage refused')
    rejected(lambda:retention.safe(root,'../outside.pdf'),'traversal refused')
    junction_check(database,root)
    # TOCTOU reference change after inventory, tested against REAL PostgreSQL.
    row=retention.sql(database,"SELECT id::text||'|'||coalesce(id_document_key,'') FROM verification_cases WHERE id_document_key IS NOT NULL LIMIT 1;")
    assert row;ident,original=row.split('|',1);assert str(uuid.UUID(ident))==ident
    retention.sql(database,f"UPDATE verification_cases SET id_document_key='{old_key}' WHERE id='{ident}';")
    rejected(lambda:retention.quarantine(database,root,batch),'new reference blocks entire batch')
    assert retention.safe(root,old_key).is_file()
    retention.sql(database,"UPDATE verification_cases SET id_document_key='"+original.replace("'","''")+f"' WHERE id='{ident}';")
    # Stale bytes must be rejected before moving any files.
    p=retention.safe(root,old_key);original_bytes=p.read_bytes();original_stat=p.stat();p.write_bytes(b'changed');os.utime(p,ns=(original_stat.st_atime_ns,original_stat.st_mtime_ns))
    rejected(lambda:retention.quarantine(database,root,batch),'changed bytes refused')
    p.write_bytes(original_bytes);os.utime(p,ns=(original_stat.st_atime_ns,original_stat.st_mtime_ns))
    result=retention.quarantine(database,root,batch);assert result['moved']==len(plan['candidates']) and not p.exists()
    again=retention.quarantine(database,root,batch);assert again['moved']==result['moved']
    assert retention.safe(root,new_key).is_file()
    for key,digest in before.items():assert hashlib.sha256(retention.safe(root,key).read_bytes()).hexdigest()==digest
    print(f"PASS: quarantined {result['moved']} old unreferenced files; repeat idempotent; all reference SHA256 unchanged",flush=True)
    restored=retention.restore(database,root,batch);assert restored['restored']==result['moved'];assert retention.restore(database,root,batch)['restored']==0
    assert retention.quarantine(database,root,batch)['status']=='already-restored'
    for row in plan['candidates']:assert retention.fingerprint(retention.safe(root,row['key']))=={k:row[k] for k in ('size','mtimeNs','sha256')}
    assert set(retention.sql(database,retention.REF_SQL).splitlines())==refs
    counts={}
    for table in ('learning_files','support_evidence','consultation_files','verification_cases'):
        counts[table]=int(retention.sql(database,'SELECT count(*) FROM '+table+';'))
        assert counts[table]>0
    assert int(retention.sql(database,"SELECT count(*) FROM learning_files WHERE category='TASK_SUBMIT';"))>0
    assert int(retention.sql(database,'SELECT count(*) FROM support_evidence WHERE deleted;'))>0
    evidence={'database':database,'root':str(root),'batchId':batch,'protectedFiles':len(refs),'candidateFiles':result['moved'],'restoredFiles':restored['restored'],'permanentlyDeleted':0,'referenceTableCounts':counts,'audit':str(root/'.retention'/'audit.jsonl'),'checks':'scope/traversal/new reference/changed bytes/age/protected SHA256/idempotency/exact restore'}
    retention.write_json(retention.TARGET/'file-retention-rehearsal-result.json',evidence)
    print('PASS: exact restore, repeat restore, task submissions, removed evidence, consultation and verification references; no permanent deletion',flush=True)
    print(json.dumps(evidence,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
