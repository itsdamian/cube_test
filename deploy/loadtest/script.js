// k6 load against backend-api inside the cluster (spec k8s-gitops-cicd, requirement 23 / AC13).
// A fixed arrival rate (not "as fast as possible"): enough to push the API far above the HPA's
// 70% CPU target, while k6 itself stays within its memory limit and prints a full summary.
// (Task 10: the first run had 30 VUs firing ~7,400 req/s and k6 was OOMKilled at 256Mi.)
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://backend-api:8080';

export const options = {
  discardResponseBodies: true,
  scenarios: {
    api: {
      executor: 'ramping-arrival-rate',
      startRate: 50,
      timeUnit: '1s',
      preAllocatedVUs: 20,
      maxVUs: 100,
      stages: [
        { duration: '30s', target: 1000 },  // ~2,000 requests/s (two requests per iteration)
        { duration: '2m30s', target: 1000 },
        { duration: '15s', target: 0 },
      ],
    },
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

export default function () {
  check(http.get(`${BASE}/api/prices/history?limit=500`), { 'history 200': (r) => r.status === 200 });
  check(http.get(`${BASE}/api/prices/converted`), { 'converted 200': (r) => r.status === 200 });
}
