import urllib.request, http.cookiejar, re

cj = http.cookiejar.CookieJar()
op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))
with op.open('http://localhost:8080/login') as r:
    html = r.read().decode('utf-8')
csrf = re.search(r'name="_csrf"\s+value="([^"]+)"', html).group(1)

login_data = f'username=DEMO-HOD&password=Demo@2027&_csrf={csrf}'.encode('utf-8')
op.open('http://localhost:8080/login', data=login_data)

with op.open('http://localhost:8080/dashboards/department') as r:
    body = r.read().decode('utf-8')
    print("STATUS:", r.status)
    for line in body.splitlines()[:40]:
        print(line)
