import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.K6_BASE_URL || 'http://localhost:5173';

export const options = {
  vus: 5,
  duration: '30s',
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
};

export default function () {
  const response = http.get(`${baseUrl}/api/v1/products?page=0&size=20`, {
    tags: { scenario: 'catalog-browse' },
  });
  check(response, {
    'catalog responds successfully': (result) => result.status === 200,
  });
  sleep(1);
}

export function handleSummary(data) {
  const duration = data.metrics.http_req_duration?.values || {};
  const metrics = {
    requestCount: data.metrics.http_reqs?.values?.count || 0,
    requestRatePerSecond: data.metrics.http_reqs?.values?.rate || 0,
    failureRate: data.metrics.http_req_failed?.values?.rate || 0,
    latencyMs: {
      p50: duration.med || 0,
      p95: duration['p(95)'] || 0,
      p99: duration['p(99)'] || 0,
    },
  };
  return { stdout: `CLOUDRESILIENCE_LOAD_SUMMARY ${JSON.stringify(metrics)}\n` };
}
