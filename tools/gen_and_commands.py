#!/usr/bin/python

import sys
import math
import threading
import urllib
import http.client
import time
import random
import socket
from urllib.parse import urlencode

id = "1234567890123"
server = 'localhost:5055'
period = 1
step = 0.001
device_speed = 40
driver_id = '123456'

waypoints = [
    (48.853780, 2.344347),
    (48.855235, 2.345852),
    (48.857238, 2.347153),
    (48.858509, 2.342563),
    (48.856066, 2.340432),
    (48.854780, 2.342230)
]

def format(coord):
    (lat, lon) = coord
    lat = 4800 + round((lat - 48) * 60, 4)
    lon = 200 + round((lon - 2) * 60, 4)
    return (lat, lon)

points = []
for i in range(0, len(waypoints)):
    (lat1, lon1) = waypoints[i]
    (lat2, lon2) = waypoints[(i + 1) % len(waypoints)]
    length = math.sqrt((lat2 - lat1) ** 2 + (lon2 - lon1) ** 2)
    count = int(math.ceil(length / step))
    for j in range(0, count):
        lat = lat1 + (lat2 - lat1) * j / count
        lon = lon1 + (lon2 - lon1) * j / count
        points.append(format((lat, lon)))

s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.connect(("localhost", 5001))

def send(lat, lon, course, speed, alarm, ignition, accuracy, rpm, fuel, driverUniqueId):
    st = "imei:" + id + ",tracker,151030080103,,F,000101.000,A,"+ str(lat) + ",N," + str(lon) +",E,"+str(speed)+",0;"
    s.send(bytearray(st.encode()))



def course(lat1, lon1, lat2, lon2):
    lat1 = lat1 * math.pi / 180
    lon1 = lon1 * math.pi / 180
    lat2 = lat2 * math.pi / 180
    lon2 = lon2 * math.pi / 180
    y = math.sin(lon2 - lon1) * math.cos(lat2)
    x = math.cos(lat1) * math.sin(lat2) - math.sin(lat1) * math.cos(lat2) * math.cos(lon2 - lon1)
    return (math.atan2(y, x) % (2 * math.pi)) * 180 / math.pi

index = 0

def listen():
    while True:
        try:
            fragment = s.recv(8196)
            print("receiving: {}".format(fragment))
        except Exception:
            pass


t2 = threading.Thread(target = listen)
t2.start()

while True:
    (lat1, lon1) = points[index % len(points)]
    (lat2, lon2) = points[(index + 1) % len(points)]
    speed = device_speed if (index % len(points)) != 0 else 0
    alarm = (index % 10) == 0
    ignition = (index % len(points)) != 0
    accuracy = 100 if (index % 10) == 0 else 0
    rpm = random.randint(500, 4000)
    fuel = random.randint(0, 80)
    driverUniqueId = driver_id if (index % len(points)) == 0 else False
    send(lat1, lon1, course(lat1, lon1, lat2, lon2), speed, alarm, ignition, accuracy, rpm, fuel, driverUniqueId)
    time.sleep(period)
    index += 1
