import http from 'k6/http';
import { check } from 'k6';
import encoding from 'k6/encoding';

export const options = {
  vus: 20,
  duration: '30s',
  thresholds: {
    http_req_duration: ['p(95)<2000'],
    http_req_failed: ['rate<0.01'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const AUTH_USER = __ENV.AUTH_USER || 'auditor';
const AUTH_PASS = __ENV.AUTH_PASS || 'change_me_auditor_pw';
const LOT_CODE = __ENV.TRACE_LOT_CODE || 'LOT-SHIP-A1';

export default function () {
  const creds = encoding.b64encode(`${AUTH_USER}:${AUTH_PASS}`);
  const headers = { Authorization: `Basic ${creds}` };

  const backRes = http.get(`${BASE_URL}/api/v1/traces/backward/${LOT_CODE}?maxDepth=6`, { headers });
  check(backRes, {
    'backward trace status 200': (r) => r.status === 200,
    'backward trace under 2000ms': (r) => r.timings.duration < 2000,
  });

  const fwdRes = http.get(`${BASE_URL}/api/v1/traces/forward/${__ENV.SOURCE_LOT_CODE || 'LOT-FAB-A1'}?maxDepth=6`, { headers });
  check(fwdRes, {
    'forward trace status 200': (r) => r.status === 200,
    'forward trace under 2000ms': (r) => r.timings.duration < 2000,
  });
}
