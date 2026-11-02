import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { generateThemeCss } from '../src/theme/generate';

const seedPath = fileURLToPath(new URL('../src/theme/seed.json', import.meta.url));
const tokensPath = fileURLToPath(new URL('../src/theme/tokens.css', import.meta.url));

const seed = JSON.parse(readFileSync(seedPath, 'utf8')) as { seedColor: string };
writeFileSync(tokensPath, generateThemeCss(seed.seedColor));
console.log(`Wrote ${tokensPath} from seed ${seed.seedColor}`);
