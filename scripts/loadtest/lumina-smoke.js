// Lumina 压测脚手架（k6）——L1 只读吞吐场景
// 用法见 docs/zh/guides/容量压测指南.md；压测纪律：mock/最便宜模型、独立库。
// 环境变量：
//   K6_BASE_URL  网关地址（默认 http://localhost:8080）
//   K6_TOKEN     登录 JWT（必填，只读接口需要认证）
//   VUS / DURATION  虚拟用户数与时长（默认 10 / 2m，阶梯加压用 -e 覆盖）

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const BASE_URL = __ENV.K6_BASE_URL || 'http://localhost:8080';
const TOKEN = __ENV.K6_TOKEN || '';

const errorRate = new Rate('lumina_error_rate');

export const options = {
  scenarios: {
    read_throughput: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '30s', target: parseInt(__ENV.VUS || '10') },
        { duration: __ENV.DURATION || '2m', target: parseInt(__ENV.VUS || '10') },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '15s',
    },
  },
  thresholds: {
    // 阈值口径见容量压测指南 L1：错误率 <0.1%，p95 < 500ms（只读接口，待实测校准）
    lumina_error_rate: ['rate<0.001'],
    http_req_duration: ['p(95)<500'],
  },
};

const params = {
  headers: {
    Authorization: `Bearer ${TOKEN}`,
    'Content-Type': 'application/json',
  },
};

export default function () {
  // L1 只读吞吐：Agent 列表轮询（代表性认证读路径）
  const list = http.get(`${BASE_URL}/api/v1/agents?pageNum=1&pageSize=10`, params);
  errorRate.add(list.status >= 400);
  check(list, { 'agents list 2xx': (r) => r.status < 400 });

  // TODO(L3)：SSE 并发连接场景——k6 原生不支持 EventSource，
  //           用 xk6-sse 扩展或独立 Node 客户端，见指南第三节
  // TODO(L2/L4)：任务提交与分治批次场景——务必指向 mock provider 压测环境

  sleep(1);
}
