# Webhook 签名验证指南

本文档说明如何验证 IoT 数据订阅平台发送的 Webhook 签名，确保推送来源可信、数据未被篡改。

---

## 签名算法

**HMAC-SHA256**

平台在创建端点时生成唯一密钥（`whsec_` 前缀），仅展示一次。请安全保存。

### 签名构造

```
signature = Base64(HMAC-SHA256(secret, timestamp + "\n" + payload))
```

| 组成部分 | 说明 |
|---------|------|
| `secret` | 端点密钥（`whsec_...`） |
| `timestamp` | Unix 时间戳（秒），通过请求头 `X-Webhook-Timestamp` 传递 |
| `payload` | 请求体原始内容（JSON 字符串） |

### 请求头

```
X-Webhook-Signature: sha256=<base64-encoded-signature>
X-Webhook-Timestamp: <unix-timestamp>
X-Webhook-Event: <event-type>
```

---

## 验证步骤

1. 从请求头获取 `X-Webhook-Timestamp` 和 `X-Webhook-Signature`
2. **防重放**：检查 `|当前时间 - timestamp| < 300秒`（5 分钟容忍）
3. 拼接签名字符串：`timestamp + "\n" + request_body`
4. 使用端点密钥计算 HMAC-SHA256
5. 比对计算结果与请求头中的签名（恒定时间比较）

---

## 各语言示例

### Python

```python
import hmac
import hashlib
import base64
import time

def verify_webhook(secret: str, timestamp: str, payload: bytes, signature: str) -> bool:
    # 1. 检查时间窗口
    if abs(time.time() - int(timestamp)) > 300:
        return False

    # 2. 拼接待签名字符串
    sign_str = f"{timestamp}\n".encode() + payload

    # 3. 计算 HMAC-SHA256
    expected = base64.b64encode(
        hmac.new(secret.encode(), sign_str, hashlib.sha256).digest()
    ).decode()

    # 4. 恒定时间比较
    return hmac.compare_digest(f"sha256={expected}", signature)

# 使用示例
from flask import request

@app.route('/webhook', methods=['POST'])
def handle_webhook():
    sig = request.headers.get('X-Webhook-Signature')
    ts  = request.headers.get('X-Webhook-Timestamp')
    secret = "whsec_your_secret_here"

    if not verify_webhook(secret, ts, request.get_data(), sig):
        return "Invalid signature", 401

    event = request.json
    print(f"Received event: {event}")
    return "", 200
```

### Node.js

```javascript
const crypto = require('crypto');

function verifyWebhook(secret, timestamp, payload, signature) {
  // 1. 检查时间窗口
  const now = Math.floor(Date.now() / 1000);
  if (Math.abs(now - parseInt(timestamp)) > 300) return false;

  // 2. 拼接待签名字符串
  const signStr = `${timestamp}\n${payload}`;

  // 3. 计算 HMAC-SHA256
  const expected = 'sha256=' + crypto
    .createHmac('sha256', secret)
    .update(signStr)
    .digest('base64');

  // 4. 恒定时间比较
  try {
    return crypto.timingSafeEqual(
      Buffer.from(expected),
      Buffer.from(signature)
    );
  } catch {
    return false;
  }
}

// Express 示例
app.post('/webhook', (req, res) => {
  const sig = req.headers['x-webhook-signature'];
  const ts  = req.headers['x-webhook-timestamp'];
  const secret = 'whsec_your_secret_here';
  const rawBody = JSON.stringify(req.body);

  if (!verifyWebhook(secret, ts, rawBody, sig)) {
    return res.status(401).send('Invalid signature');
  }

  console.log('Event:', req.body);
  res.sendStatus(200);
});
```

### Java

```java
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class WebhookVerifier {

    public static boolean verify(String secret, String timestamp,
                                  String payload, String signature) {
        // 1. 检查时间窗口
        long now = System.currentTimeMillis() / 1000;
        if (Math.abs(now - Long.parseLong(timestamp)) > 300) return false;

        // 2. 拼接待签名字符串
        String signStr = timestamp + "\n" + payload;

        // 3. 计算 HMAC-SHA256
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(signStr.getBytes(StandardCharsets.UTF_8));
            String expected = "sha256=" + Base64.getEncoder().encodeToString(hash);

            // 4. 恒定时间比较
            return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }
}
```

### Go

```go
package webhook

import (
    "crypto/hmac"
    "crypto/sha256"
    "encoding/base64"
    "fmt"
    "strconv"
    "strings"
    "time"
)

func Verify(secret, timestamp, payload, signature string) bool {
    // 1. 检查时间窗口
    ts, err := strconv.ParseInt(timestamp, 10, 64)
    if err != nil {
        return false
    }
    if abs(time.Now().Unix()-ts) > 300 {
        return false
    }

    // 2. 拼接待签名字符串
    signStr := fmt.Sprintf("%s\n%s", timestamp, payload)

    // 3. 计算 HMAC-SHA256
    mac := hmac.New(sha256.New, []byte(secret))
    mac.Write([]byte(signStr))
    expected := "sha256=" + base64.StdEncoding.EncodeToString(mac.Sum(nil))

    // 4. 恒定时间比较
    return hmac.Equal([]byte(expected), []byte(signature))
}

func abs(x int64) int64 {
    if x < 0 { return -x }
    return x
}
```

---

## 推送负载格式

```json
{
  "eventId": "evt-550e8400-e29b-41d4-a716-446655440000",
  "eventType": "THRESHOLD_ALERT",
  "timestamp": "2026-04-24T08:30:00Z",
  "subscriptionId": 1,
  "subscriptionName": "高温告警订阅",
  "deviceId": 1,
  "deviceName": "温度传感器-A01",
  "dataPoint": {
    "identifier": "temperature",
    "value": 35.2,
    "unit": "°C",
    "reportedAt": "2026-04-24T08:29:55Z"
  },
  "rule": {
    "id": 1,
    "type": "THRESHOLD",
    "condition": { "operator": "gt", "threshold": 30 },
    "priority": "Critical"
  }
}
```

## 事件类型

| eventType | 说明 |
|-----------|------|
| `THRESHOLD_ALERT` | 阈值告警 |
| `RATE_CHANGE_ALERT` | 变化率告警 |
| `DEVICE_OFFLINE` | 设备离线 |
| `DATA_MISSING` | 数据缺失 |

## 最佳实践

1. **恒定时间比较**：始终使用语言内置的恒定时间比较函数，避免时序攻击
2. **时间窗口**：建议 5 分钟容忍，拒绝过期的重放请求
3. **密钥轮换**：定期更新端点密钥，旧密钥保留 24 小时过渡期
4. **幂等处理**：同一 `eventId` 的推送可能因重试而送达多次，请做幂等检查
5. **超时响应**：请在 5 秒内返回 HTTP 200，否则平台将视为推送失败
