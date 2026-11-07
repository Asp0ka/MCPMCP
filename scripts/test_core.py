import argparse, pathlib, subprocess, sys
p=argparse.ArgumentParser();p.add_argument('--jdk',required=True);p.add_argument('--gson',required=True);a=p.parse_args()
r=pathlib.Path(__file__).resolve().parents[1];out=r/'core/build/contract';out.mkdir(parents=True,exist_ok=True)
sources=[str(x) for folder in ['core/src/main/java','core/src/test/java','plugin-api/src/main/java'] for x in (r/folder).rglob('*.java')]
jdk=pathlib.Path(a.jdk)/'bin';cp=a.gson
c=subprocess.run([str(jdk/'javac.exe'),'-encoding','UTF-8','--release','17','-cp',cp,'-d',str(out),*sources]);
if c.returncode:sys.exit(c.returncode)
sys.exit(subprocess.run([str(jdk/'java.exe'),'-cp',str(out)+';'+cp,'dev.mcpmcp.core.CoreTest']).returncode)
