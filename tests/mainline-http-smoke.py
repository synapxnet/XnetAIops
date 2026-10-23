#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# Copyright (C) 2026 Synapxnet. All rights reserved.
# Synapxnet Proprietary and Confidential. No copying, distribution or use without authorization.
"""本机隔离数据库与真实HTTP验收 / Isolated local database and real HTTP verification.
Author: maoyo | Department: 研发部 | Date: 2026-10-23 | Version: 1.3.0
Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com
"""
import argparse
import base64
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import threading
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
PARSER = argparse.ArgumentParser()
PARSER.add_argument('--dependencies', type=Path, required=True)
PARSER.add_argument('--java', type=Path, required=True)
PARSER.add_argument('--output', type=Path, required=True)
ARGS = PARSER.parse_args()
ARGS.output.mkdir(parents=True, exist_ok=True)
SCHEMA = 'aiops_mainline_http_' + secrets.token_hex(8)
DELIVERED = {}
CHECKS = []
PROCESSES = []
LOGS = []
MYSQL = ['docker', 'exec', '-i', 'synapxnet-mainline-mysql-20261023', 'sh', '-c', 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4']


class SmsReceiver(BaseHTTPRequestHandler):
    """仅本机接收验证码，不发送真实短信。 Accept local codes without sending real messages."""

    def do_POST(self):
        """保留一次性码于内存，不写日志。 Keep codes in memory and out of logs."""
        body = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
        DELIVERED[body['phone']] = body['code']
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()
        self.wfile.write(b'{"accepted":true}')

    def log_message(self, *_args):
        """禁止测试接收器打印请求。 Silence receiver request logs."""


def sql(statement):
    """仅向专用测试MySQL容器执行SQL，不输出密码或SQL内容。 Execute only in the dedicated test container without echoing SQL or credentials."""
    result = subprocess.run(MYSQL, input=statement.encode('utf-8'), stdout=subprocess.PIPE, stderr=subprocess.PIPE, creationflags=subprocess.CREATE_NO_WINDOW)
    if result.returncode:
        raise RuntimeError(result.stderr.decode('utf-8', errors='replace'))
    return result.stdout.decode('utf-8')


def request(port, method, path, body=None, token=None):
    """调用仅绑定本机的原生服务，不记录令牌。 Call loopback-only native services without recording tokens."""
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = Request(f'http://127.0.0.1:{port}{path}', data=None if body is None else json.dumps(body).encode(), headers=headers, method=method)
    try:
        with urlopen(req, timeout=12) as response:
            return response.status, json.loads(response.read())
    except HTTPError as error:
        return error.code, json.loads(error.read())


def check(label, condition):
    """保存无敏感字段的验收结论。 Record a result without sensitive fields."""
    CHECKS.append({'check': label, 'passed': bool(condition)})
    if not condition:
        raise AssertionError(label)


def start(module, port, env):
    """通过随机schema启动真实jar，固定本机测试端口。 Start the real jar against a random schema on a loopback test port."""
    jar = ROOT / f'aiops-{module}-service/target/aiops-{module}-service-1.3.0.jar'
    log = (ARGS.output / f'http-{module}.log').open('wb')
    LOGS.append(log)
    command = [str(ARGS.java), '-Xmx384m', '-jar', str(jar), '--server.address=127.0.0.1', f'--server.port={port}', f'--spring.datasource.url=jdbc:mysql://127.0.0.1:13306/{SCHEMA}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC', '--logging.level.com.synapxnet=WARN', '--logging.level.root=WARN']
    process = subprocess.Popen(command, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT, creationflags=subprocess.CREATE_NO_WINDOW)
    PROCESSES.append(process)
    for _ in range(80):
        if process.poll() is not None:
            raise RuntimeError(f'{module} exited; see http-{module}.log')
        try:
            probe = {'usr': '/users', 'clm': '/clusters', 'hom': '/hosts', 'svm': '/services', 'mon': '/alerts', 'reg': '/registries', 'k8s': '/clusters'}[module]
            request(port, 'GET', f'/api/{module}{probe}')
            return process
        except (URLError, TimeoutError, ConnectionError):
            time.sleep(0.5)
    raise RuntimeError(f'{module} did not start')


def envelope(port, method, path, body=None, token=None):
    """同时检查HTTP状态与业务包络。 Check both HTTP status and native envelope."""
    status, result = request(port, method, path, body, token)
    check(f'{method} {path}', status == 200 and result.get('code') == 0)
    return result.get('data')


server = HTTPServer(('127.0.0.1', 0), SmsReceiver)
threading.Thread(target=server.serve_forever, daemon=True).start()
phone = '199' + str(secrets.randbelow(100_000_000)).zfill(8)
user_phone = '198' + str(secrets.randbelow(100_000_000)).zfill(8)
try:
    config = json.loads(ARGS.dependencies.read_text(encoding='utf-8'))
    env = dict(os.environ, DB_PASSWORD=config['mysql']['password'], JWT_SECRET=base64.b64encode(secrets.token_bytes(48)).decode(), K8S_ENCRYPTION_KEY=secrets.token_hex(16), REDIS_HOST='127.0.0.1', REDIS_PORT='16379', XNET_SMS_GATEWAY_URL=f'http://127.0.0.1:{server.server_port}/sms', XNET_SMS_GATEWAY_TOKEN='isolated-http-test', OPENXNET_REHEARSAL_ENABLED='false')
    schema_sql = (ROOT / 'XnetAIops.sql').read_text(encoding='utf-8').replace('XnetAIops', SCHEMA)
    schema_sql += '\n' + (ROOT / 'devops-tables.sql').read_text(encoding='utf-8').replace('XnetAIops', SCHEMA)
    sql(schema_sql)
    sql(f'USE {SCHEMA};\n' + (ROOT / 'devops-tables.sql').read_text(encoding='utf-8'))
    check('DevOps schema can be applied twice on MySQL 8', True)
    sql(f'USE {SCHEMA}; ALTER TABLE xnet_aiops_usr_organization_membership DROP COLUMN data_access_enabled;')
    migration = (ROOT / 'database/migrations/20261023-mainline-organization-access.sql').read_text(encoding='utf-8')
    sql(f'USE {SCHEMA};\n' + migration + '\n' + migration)
    access_default = sql(f"SELECT column_default FROM information_schema.columns WHERE table_schema='{SCHEMA}' AND table_name='xnet_aiops_usr_organization_membership' AND column_name='data_access_enabled';")
    check('organization access migration upgrades and reapplies without granting access', access_default.strip().splitlines()[-1] == '0')
    sql(f"USE {SCHEMA}; INSERT INTO xnet_aiops_usr_user(uid,username,password,phone,user_type,status) VALUES ('MAINLINE-HTTP-ADMIN','local_admin','unused-test-password','{phone}','admin','active'),('MAINLINE-HTTP-USER','local_user','unused-test-password','{user_phone}','user','active');")
    usr = start('usr', 19185, env)
    check('anonymous user administration denied', request(19185, 'GET', '/api/usr/users')[0] == 401)
    check('fixed competition code rejected', request(19185, 'POST', '/api/usr/login', {'userPhone': phone, 'code': '000000'})[1].get('code') != 0)
    envelope(19185, 'POST', '/api/usr/sms-code', {'userPhone': phone})
    login = envelope(19185, 'POST', '/api/usr/login', {'userPhone': phone, 'code': DELIVERED[phone]})
    token = login['accessToken']
    check('one-time code cannot be reused', request(19185, 'POST', '/api/usr/login', {'userPhone': phone, 'code': DELIVERED[phone]})[1].get('code') != 0)
    envelope(19185, 'GET', '/api/usr/user/info', token=token)
    envelope(19185, 'GET', '/api/usr/auth/codes', token=token)
    envelope(19185, 'GET', '/api/usr/organization-tree', token=token)
    users = envelope(19185, 'GET', '/api/usr/users', token=token)
    check('passwords are not serialized', all('password' not in user for user in users))
    envelope(19185, 'GET', '/api/usr/roles', token=token)
    envelope(19185, 'POST', '/api/usr/sms-code', {'userPhone': user_phone})
    ordinary = envelope(19185, 'POST', '/api/usr/login', {'userPhone': user_phone, 'code': DELIVERED[user_phone]})['accessToken']
    check('ordinary user cannot manage roles', request(19185, 'POST', '/api/usr/user-roles', {}, ordinary)[0] == 403)
    clm = start('clm', 19181, env)
    check('new mainline database has no demo clusters', envelope(19181, 'GET', '/api/clm/clusters', token=token) == [])
    cluster = envelope(19181, 'POST', '/api/clm/clusters', {'clusterName': '本地验收集群', 'clusterCode': 'mainline-local-test', 'clusterType': 'custom'}, token)
    cid = cluster['id']
    check('cluster create has persistent identifier', isinstance(cid, int))
    loaded = envelope(19181, 'GET', f'/api/clm/clusters/{cid}', token=token)
    check('cluster fields survive database round trip', loaded['clusterName'] == '本地验收集群' and loaded['clusterCode'] == 'mainline-local-test')
    envelope(19181, 'PUT', f'/api/clm/clusters/{cid}', dict(loaded, description='updated'), token)
    envelope(19181, 'GET', f'/api/clm/clusters/{cid}/overview', token=token)
    envelope(19181, 'POST', f'/api/clm/clusters/{cid}/variables', {'variableName': 'test-key', 'variableValue': 'local-value'}, token)
    variables = envelope(19181, 'GET', f'/api/clm/clusters/{cid}/variables', token=token)
    check('cluster variable persisted', len(variables) == 1)
    envelope(19181, 'DELETE', f"/api/clm/clusters/variables/{variables[0]['id']}", token=token)
    envelope(19181, 'DELETE', f'/api/clm/clusters/{cid}', token=token)
    check('cluster delete persisted', envelope(19181, 'GET', '/api/clm/clusters', token=token) == [])
    clm.terminate(); clm.wait(timeout=15)
    for module, port, endpoints in [('hom',19182,['/hosts','/racks?clusterId=1']),('svm',19183,['/services']),('mon',19184,['/alerts']),('reg',19187,['/registries']),('k8s',19186,['/clusters','/devops/projects'])]:
        process = start(module, port, env)
        for endpoint in endpoints:
            envelope(port, 'GET', f'/api/{module}{endpoint}', token=token)
        process.terminate(); process.wait(timeout=15)
    envelope(19185, 'POST', '/api/usr/logout', token=token)
    check('logged-out administrator rejected', request(19185, 'GET', '/api/usr/users', token=token)[0] == 401)
finally:
    for process in PROCESSES:
        if process.poll() is None:
            process.terminate()
            try: process.wait(timeout=15)
            except subprocess.TimeoutExpired: process.kill(); process.wait()
    for log in LOGS: log.close()
    server.shutdown()
    if re.fullmatch(r'aiops_mainline_http_[a-f0-9]{16}', SCHEMA):
        sql(f'DROP DATABASE IF EXISTS {SCHEMA};')
    (ARGS.output / 'mainline-http-results.json').write_text(json.dumps({'metadata': {'copyright': 'Copyright (C) 2026 Synapxnet. All rights reserved.', 'confidentiality': 'Proprietary and Confidential', 'author': 'maoyo', 'department': '研发部', 'date': '2026-10-23', 'version': '1.3.0', 'securityLevel': 'INTERNAL'}, 'environment':'Isolated random MySQL schema + real service jars + Redis + loopback SMS receiver; no live platform or external model calls','checks':CHECKS}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'checks':len(CHECKS),'passed':sum(item['passed'] for item in CHECKS)}, ensure_ascii=False))
