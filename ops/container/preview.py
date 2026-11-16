"""TS-26 disposable browser fixture: 20 rooms and 40 bookings, loopback only."""
import datetime as dt
import json
import time
from runtime import Client, Fixture, ROOT

stop = ROOT / "target/stop-browser-preview"
stop.unlink(missing_ok=True)
with Fixture() as fixture:
    client = Client(fixture.base)
    client.login()
    today = dt.date.fromisoformat(json.loads(client.request("GET", "/api/v1/occupancy")[2])["date"])
    for index, name in enumerate(["Альфа", "Бета", "Гамма", "Ґанок", "Дельта", "Екран", "Єдність", "Жовта", "Зал", "Ідея",
                                  "Їжак", "Калина", "Липа", "Мрія", "Нива", "Обрій", "Простір", "Рада", "Сонце", "Тиша"]):
        assert client.request("POST", "/api/v1/rooms", {"name":name,"floor":1+index//10,"capacity":4+index%9,"equipment":"Екран, дошка"})[0] == 201
    fixture.sql("""
        INSERT INTO app_user(username,username_key,display_name,password_hash,role,enabled)
        SELECT 'preview.employee','preview.employee','Олена Коваленко',password_hash,'EMPLOYEE',true
        FROM app_user WHERE username='bootstrap.admin';
        INSERT INTO booking(room_id,author_id,title,start_at,end_at,status,created_at)
        SELECT r.id, u.id, CASE WHEN h=10 THEN 'Планування команди' ELSE 'Огляд проєкту' END,
               (CURRENT_TIMESTAMP AT TIME ZONE 'Europe/Kyiv')::date+interval '1 day'+h*interval '1 hour',
               (CURRENT_TIMESTAMP AT TIME ZONE 'Europe/Kyiv')::date+interval '1 day'+(h+1)*interval '1 hour', 'ACTIVE',now()-interval '1 day'
        FROM room r CROSS JOIN (VALUES(10),(18)) hours(h)
        JOIN app_user u ON u.username=CASE WHEN r.id%2=0 THEN 'bootstrap.admin' ELSE 'preview.employee' END;
        """)
    info = {"base":fixture.base,"today":str(today),"date":str(today+dt.timedelta(days=1)),
            "image":fixture.image,"imageId":fixture.info["Image"],"rooms":20,"bookings":40,
            "stopFile":str(stop)}
    (ROOT/"target/browser-preview.json").write_text(json.dumps(info,indent=2),encoding="utf-8")
    print(json.dumps(info),flush=True)
    deadline = time.monotonic()+7200
    while not stop.exists() and time.monotonic()<deadline:
        time.sleep(1)
