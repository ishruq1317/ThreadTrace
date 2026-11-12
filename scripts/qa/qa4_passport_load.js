import http from 'k6/http';
import { check } from 'k6';

export const options = {
  vus: 30,
  duration: '30s',
  thresholds: {
    http_req_duration: ['p(95)<2000'],
    http_req_failed: ['rate<0.01'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PASSPORT_CODE = __ENV.PASSPORT_CODE || 'PP-A1';

export default function () {
  const htmlRes = http.get(`${BASE_URL}/p/${PASSPORT_CODE}`);
  check(htmlRes, {
    'public HTML status 200': (r) => r.status === 200,
    'public HTML has ETag': (r) => Boolean(r.headers['ETag'] || r.headers['Etag']),
    'public HTML under 2000ms': (r) => r.timings.duration < 2000,
  });

  const jsonRes = http.get(`${BASE_URL}/api/v1/public/passports/${PASSPORT_CODE}`);
  check(jsonRes, {
    'public JSON status 200': (r) => r.status === 200,
    'public JSON under 2000ms': (r) => r.timings.duration < 2000,
  });
}
