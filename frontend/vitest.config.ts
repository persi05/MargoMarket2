import { defineConfig } from 'vitest/config';
export default defineConfig({ test: { pool: 'forks', maxWorkers: 1 } });
