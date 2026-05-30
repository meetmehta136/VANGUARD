import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const baseUrl = 'http://localhost:8081';
const failureRate = new Rate('failed_requests');
const latencyTrend = new Trend('latency');

export let options = {
    stages: [
        { duration: '30s', target: 20 },
        { duration: '2m', target: 50 },
        { duration: '30s', target: 0 },
    ],
    thresholds: {
        http_req_duration: ['p(99)<500'],
        http_req_failed: ['rate<0.15'],
    },
};

const userIds = ['user-lt-001', 'user-lt-002', 'user-lt-003', 'user-lt-004', 'user-lt-005'];
const merchants = ['merch-a', 'merch-b', 'merch-c', 'merch-d', 'merch-e'];
const types = ['PAYMENT', 'TRANSFER', 'WITHDRAWAL'];

function randomPayload() {
    const user = userIds[Math.floor(Math.random() * userIds.length)];
    const merchant = merchants[Math.floor(Math.random() * merchants.length)];
    const type = types[Math.floor(Math.random() * types.length)];
    const amount = parseFloat((Math.random() * 500 + 10).toFixed(2));
    const txnId = `txn-k6-${Date.now()}-${Math.random().toString(36).substring(2, 8)}`;
    return JSON.stringify({
        transactionId: txnId,
        userId: user,
        amount: amount,
        transactionType: type,
        merchantId: merchant,
        merchantCategory: 'retail',
        latitude: 40.7128 + (Math.random() - 0.5) * 10,
        longitude: -74.006 + (Math.random() - 0.5) * 10,
        deviceId: `device-${Math.floor(Math.random() * 100)}`,
        ipAddress: `10.0.${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 255)}`,
        currency: 'USD',
        timestamp: new Date().toISOString(),
    });
}

export default function () {
    group('submit transaction', () => {
        const payload = randomPayload();
        const res = http.post(`${baseUrl}/api/v1/transactions`, payload, {
            headers: { 'Content-Type': 'application/json' },
        });

        latencyTrend.add(res.timings.duration);
        failureRate.add(res.status !== 202);

        check(res, {
            'status is 202': (r) => r.status === 202,
            'transaction ID returned': (r) => r.json('transactionId') !== '',
        });
    });

    sleep(0.5);
}
