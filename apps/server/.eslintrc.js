module.exports = {
  parser: '@typescript-eslint/parser',
  parserOptions: {
    project: 'tsconfig.json',
    sourceType: 'module',
  },
  plugins: ['@typescript-eslint'],
  extends: ['plugin:@typescript-eslint/recommended'],
  root: true,
  env: { node: true, jest: true },
  ignorePatterns: ['.eslintrc.js', 'dist', 'node_modules'],
  rules: {
    '@typescript-eslint/interface-name-prefix': 'off',
    '@typescript-eslint/explicit-function-return-type': 'off',
    '@typescript-eslint/no-explicit-any': 'warn',
    // 门禁4 硬规则：业务模块禁止裸 SQL / 绕过 ScopeQueryBuilder（ADR §1.5 第三层）
    // 规则实现在 eslint-rules/no-raw-sql.js，由 `npm run lint` 的 --rulesdir 加载
    'no-raw-sql': 'error',
  },
};
