// F3 半活体 e2e：OpenMetadata stub（返回三源表注释，OM REST 形状）
// 用法: node om-stub.js [port]   默认 18585
const http = require('http');
const port = parseInt(process.argv[2] || '18585', 10);

const tables = {
  data: [
    {
      fullyQualifiedName: 'mysql.customer_db.customer',
      description: '客户基础表：姓名、手机号、身份证、等级、地区',
      columns: [
        { name: 'cust_id', description: '客户ID' },
        { name: 'cust_name', description: '客户姓名' },
        { name: 'phone', description: '手机号' },
        { name: 'cust_level', description: '客户等级 VIP1-VIP3' },
        { name: 'region', description: '所在地区' }
      ]
    },
    {
      fullyQualifiedName: 'clickhouse.orders_db.orders',
      description: '客户订单事实表',
      columns: [
        { name: 'order_id', description: '订单号' },
        { name: 'cust_id', description: '客户ID' },
        { name: 'order_amount', description: '订单金额' },
        { name: 'order_time', description: '下单时间' }
      ]
    },
    {
      fullyQualifiedName: 'postgres.external.risk_tags',
      description: '客户风险标签',
      columns: [
        { name: 'cust_id', description: '客户ID' },
        { name: 'risk_level', description: '风险等级 high/medium/low' },
        { name: 'risk_score', description: '风险分 0-100' }
      ]
    }
  ]
};

http.createServer((req, res) => {
  if (req.url.startsWith('/api/v1/tables')) {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(tables));
    console.log(`[${new Date().toISOString()}] GET ${req.url} -> 200 (3 tables)`);
  } else {
    res.writeHead(404);
    res.end('{}');
  }
}).listen(port, () => console.log(`OM stub on :${port}`));
