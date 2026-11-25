// Run: node devtools/check-tab-hover.mjs. Compiles actual production TabStrip/helpers; no game/GPU.
import {fileURLToPath} from 'node:url';
import {join} from 'node:path';
import {spawnSync} from 'node:child_process';
const root=fileURLToPath(new URL('../',import.meta.url));
const home=process.env.JAVA_HOME;
const java=home ? join(home,'bin',process.platform==='win32'?'java.exe':'java') : 'java';
const result=spawnSync(java,['devtools/TabHoverProbe.java'],{cwd:root,stdio:'inherit'});
if(result.error) console.error(result.error.message);
process.exit(result.status ?? 1);
