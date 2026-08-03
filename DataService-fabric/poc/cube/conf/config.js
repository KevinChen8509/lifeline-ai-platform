// Cube.dev 配置
// 文档：https://cube.dev/docs/reference/configuration
//
// 数据源：Trino（已经联邦了 mysql / clickhouse / postgres 三个 catalog）
// 不直接连各个源，让 Trino 透明下推 JOIN，复用 Week 1 的虚拟数据层。

module.exports = {
  // schemas 目录：Cube 启动时从这里加载 *.yml
  schemaPath: '/cube/conf/schema',

  // Trino 作为统一数据源
  dbType: 'trino',
  driverFactory: () => {
    const TrinoDriver = require('@cubejs-backend/trino-driver');
    return new TrinoDriver({
      host: process.env.CUBEJS_DB_HOST || 'trino',
      port: process.env.CUBEJS_DB_PORT ? Number(process.env.CUBEJS_DB_PORT) : 8080,
      user: process.env.CUBEJS_DB_USER || 'default',
      catalog: process.env.CUBEJS_DB_NAME || 'mysql',
      schema: process.env.CUBEJS_DB_SCHEMA || 'customer_db',
      // Trino 435 默认无 SSL
      ssl: false,
    });
  },

  // Redis（生产模式必需，PoC 用 dev mode 跳过）
  // redisUrl: process.env.CUBEJS_REDIS_URL,

  // 预聚合持久化：PoC 阶段不持久化，热加载 schema 即可
  scheduledRefreshTimer: false,

  // 日志级别
  telemetry: false,
};
