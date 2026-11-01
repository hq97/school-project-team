/**
 * 门禁4 第三层的牙：封堵绕过 ScopeQueryBuilder 的裸 SQL / 裸 Prisma 查询。
 *
 * 规则（ADR §1.5 ③）：
 *  1. 业务模块（src/modules/**）禁止出现 prisma.$queryRaw / $executeRaw / $queryRawUnsafe；
 *  2. 业务模块禁止绕过 ScopeQueryBuilder 直查 prisma.<model>.find*/count/aggregate——
 *     **按方法（AST 所在函数）判定**：该方法体内出现 prisma 模型查询、却没有
 *     `scopeQuery` / `ScopeQueryBuilder` 调用时报错；
 *  3. 平台层（src/common/scope/**）豁免——只有它是数据层唯一出口。
 *
 * 门禁4 审计修正：原实现按**整个文件**做文本匹配——文件里只要有一处 `scopeQuery`，
 * 全文件所有裸查都被豁免（space.controller 曾因此三处裸查整体漏检）。现改为逐方法判定。
 *
 * 说明：仍是启发式（门禁5 Code Review + 启动期 DataScope 扫描共同兜底），
 * 但粒度从"文件级"收紧到"方法级"后，单文件内多方法混排不再互相豁免。
 */
module.exports = {
  meta: {
    type: 'problem',
    docs: {
      description: '禁止业务模块绕过 ScopeQueryBuilder 直接裸查数据库（ADR §1.5）',
    },
    schema: [],
    messages: {
      noRawSql:
        '禁止在业务模块使用 prisma.$queryRaw/$executeRaw 等裸 SQL —— 所有数据访问必须经 ScopeQueryBuilder（ADR §1.5 第三层）。',
      noDirectModelQuery:
        '本方法使用了 prisma.<model>.find*/count/aggregate，但方法体内没有 scopeQuery / ScopeQueryBuilder —— 疑似绕过数据层唯一出口。请改用 ScopeQueryBuilder（读）或在其确认下写入。',
    },
  },
  create(context) {
    const filename = context.getFilename().replace(/\\/g, '/');
    const isPlatformScope = filename.includes('/src/common/scope/');
    const isBusinessModule = filename.includes('/src/modules/');
    if (!isBusinessModule || isPlatformScope) return {};

    const sourceCode = context.getSourceCode();

    /** 判定：这是不是一次 prisma.<model>.<readMethod>( 调用 */
    function isPrismaModelQuery(callNode) {
      const callee = callNode.callee;
      if (!callee || callee.type !== 'MemberExpression' || callee.computed) return false;
      const methodProp = callee.property;
      if (!methodProp || methodProp.type !== 'Identifier') return false;
      if (
        !['findMany', 'findFirst', 'findUnique', 'count', 'aggregate', 'groupBy'].includes(
          methodProp.name,
        )
      ) {
        return false;
      }
      // prisma.<model>.<method>：callee.object 本身是 MemberExpression
      const receiver = callee.object;
      return !!receiver && receiver.type === 'MemberExpression';
    }

    /** 判定：这是不是一次 prisma.$queryRaw / $executeRaw* 调用（不管 receiver） */
    function isRawSqlCall(callNode) {
      const callee = callNode.callee;
      if (!callee || callee.type !== 'MemberExpression') return false;
      const methodProp = callee.property;
      if (!methodProp || methodProp.type !== 'Identifier') return false;
      return /^\$(queryRaw|executeRaw|queryRawUnsafe|executeRawUnsafe|runCommandRaw)/.test(
        methodProp.name,
      );
    }

    return {
      'CallExpression'(node) {
        if (isRawSqlCall(node)) {
          context.report({ node, messageId: 'noRawSql' });
          return;
        }
        if (!isPrismaModelQuery(node)) return;

        // 找到所属函数（含箭头函数/类方法），方法体内必须有 scopeQuery 使用痕迹
        let fn = null;
        for (let p = node.parent; p; p = p.parent) {
          if (
            p.type === 'FunctionDeclaration' ||
            p.type === 'FunctionExpression' ||
            p.type === 'ArrowFunctionExpression' ||
            p.type === 'MethodDefinition'
          ) {
            fn = p;
            break;
          }
          // 顶层方法级装饰器下的表达式直接属于类方法，遇到 Program/ClassBody 停止
          if (p.type === 'Program' || p.type === 'ClassBody') break;
        }
        const scopeText = fn ? sourceCode.getText(fn) : sourceCode.getText();
        const scopeIdSeen = /scopeQuery|scopeQueryBuilder|ScopeQueryBuilder/.test(scopeText);
        if (!scopeIdSeen) {
          context.report({ node, messageId: 'noDirectModelQuery' });
        }
      },
    };
  },
};
