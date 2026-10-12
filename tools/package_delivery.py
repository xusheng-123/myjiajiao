"""Package only the tested source/build and operational templates; exclude local data/secrets."""
import hashlib,json,pathlib,subprocess,sys,zipfile
from delivery_acceptance import scope,ROOT
sys.stdout.reconfigure(encoding='utf-8')
def main():
    s,out=scope();dest=ROOT/'deploy/dist';dest.mkdir(parents=True,exist_ok=True)
    artifact=dest/('nurture-delivery-'+out.name.removeprefix('delivery-acceptance-')+'.zip')
    if artifact.exists():raise ValueError('Existing delivery archive must not be overwritten')
    backend=pathlib.Path(s['backendBuild']);frontend=pathlib.Path(s['frontendBuild']);jar=backend/'target/nurture-backend-1.0.0.jar';browser=frontend/'dist/nurture-frontend/browser'
    if not jar.is_file() or not (browser/'index.html').is_file():raise ValueError('Verified build missing')
    for source,copy in [(ROOT/'backend/src',backend/'src'),(ROOT/'frontend/src',frontend/'src'),(ROOT/'frontend/public',frontend/'public')]:
        names={p.relative_to(source) for p in source.rglob('*') if p.is_file()}
        if names!={p.relative_to(copy) for p in copy.rglob('*') if p.is_file()}:raise ValueError('Build source file list differs')
        for name in names:
            if (source/name).read_bytes()!=(copy/name).read_bytes():raise ValueError('Build source differs: '+str(name))
    entries=[]
    def add(file,name):
        if file.is_symlink():raise ValueError('Packaging links is forbidden')
        entries.append((file,name))
    add(jar,'backend/nurture-backend-1.0.0.jar')
    add(ROOT/'docs/交付说明_2026-10-12.md','README.md')
    for file in browser.rglob('*'):
        if file.is_file():add(file,'frontend-static/'+file.relative_to(browser).as_posix())
    for base,prefix in [(backend/'src','source/backend/src'),(frontend/'src','source/frontend/src'),(frontend/'public','source/frontend/public'),(frontend/'scripts','source/frontend/scripts')]:
        for file in base.rglob('*'):
            if file.is_file():add(file,prefix+'/'+file.relative_to(base).as_posix())
    add(backend/'pom.xml','source/backend/pom.xml')
    for file in frontend.glob('*.json'):add(file,'source/frontend/'+file.name)
    for name in ['AGENTS.md','PROJECT_HANDOFF.md','README.md','.gitignore']:
        add(ROOT/name,'source/'+name)
    for file in (ROOT/'deploy').iterdir():
        if file.is_file() and (file.name.startswith('docker-compose') or file.name.endswith('.example') or file.suffix in ('.sh','.py','.md')):add(file,'deploy/'+file.name)
    for file in (ROOT/'tools').glob('*'):
        if file.is_file() and file.suffix in ('.py','.cjs'):add(file,'source/tools/'+file.name)
    for name in ['交付报告_全功能验收_2026-10-12.md','交付说明_2026-10-12.md','FILE_RETENTION.md','PROJECT_CONTEXT.md','ARCHITECTURE.md','DECISIONS.md','CHANGELOG.md']:
        add(ROOT/'docs'/name,'docs/'+name)
    for file in (ROOT/'docs').rglob('*.md'):add(file,'source/docs/'+file.relative_to(ROOT/'docs').as_posix())
    for folder in ['delivery-routes','delivery-transactions','delivery-purchase','delivery-offline','delivery-operations','delivery-legacy','learning-plans','learning-materials','recurring-schedule','assessments-messages','support-course','file-quota']:
        for file in (out/'.walk'/folder).glob('*.png'):
            if file.name!='failure.png':add(file,'evidence/screenshots/'+folder+'/'+file.name)
    add(out/'acceptance-summary.json','evidence/acceptance-summary.json')
    add(ROOT.parent/'项目交接.md','docs/项目交接_滚动事实源.md')
    commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
    manifest={'scope':'local functional delivery, not production deployment','sourceBaseCommitAtStart':s['head'],'repositoryCommitAtPackaging':commit,'sourceIncludesAcceptanceChanges':True,'branch':s['branch'],'migrationVersions':list(range(1,23)),'files':[{ 'path':name,'size':file.stat().st_size,'sha256':hashlib.sha256(file.read_bytes()).hexdigest()} for file,name in entries]}
    if len({name for _,name in entries})!=len(entries):raise ValueError('Duplicate archive paths')
    with zipfile.ZipFile(artifact,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for file,name in entries:z.write(file,name)
        z.writestr('MANIFEST.json',json.dumps(manifest,ensure_ascii=False,indent=2))
    with zipfile.ZipFile(artifact) as z:
        assert z.testzip() is None
        for row in manifest['files']:assert hashlib.sha256(z.read(row['path'])).hexdigest()==row['sha256']
        assert not any('/node_modules/' in n or '.env'==pathlib.PurePosixPath(n).name or 'browser-profile' in n or n.endswith('.dump') for n in z.namelist())
    digest=hashlib.sha256(artifact.read_bytes()).hexdigest();artifact.with_suffix('.sha256').write_text(digest+'  '+artifact.name+'\n',encoding='utf-8')
    (out/'package-result.json').write_text(json.dumps({'archive':str(artifact),'bytes':artifact.stat().st_size,'sha256':digest,'entries':len(entries),'allEntryHashesVerified':True},ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'archive':str(artifact),'bytes':artifact.stat().st_size,'entries':len(entries),'sha256':digest},ensure_ascii=False))
if __name__=='__main__':main()
