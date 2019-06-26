import time
import json
import socket
from enum import Enum, auto

from threading import Timer
from Robot.UR.URRobot import URRobot

# Start camera and view it
# camera = Camera().start()
# camera.show()

host = "192.168.0.11"
robot = URRobot(host)

# Set TCP offset
robot.set_tcp((0.05, -0.05, 0.295, 0, 0, 0))
time.sleep(0.5)

# Set the starting position ((-0.10, -0.8, 0.3, 0, 3.14, 0))
robot.reset_position()
time.sleep(10)


class Motion(Enum):
    DOWN = auto()
    UP = auto()


threshold = 40
blink_threshold = 50

stopTask = None
cut_off = False


def refresh_stop_task():
    """
        Reschedules robot cutoff to 5 seconds from now
    """
    global stopTask

    def stop():
        global cut_off
        cut_off = True
        print('No new data, stopping')
        robot.stopj()

    if stopTask is not None:
        stopTask.cancel()

    stopTask = Timer(15.0, stop)
    stopTask.start()
    cut_off = False


if __name__ == '__main__':
    # open connection to the headset
    headset = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    headset.connect(('localhost', 13854))

    # send configuration and start listening
    headset.send(b"{\"enableRawData\": false, \"format\": \"Json\"}")
    time.sleep(1)

    inStream = headset.makefile(encoding='utf8', errors='ignore')
    line = ''
    while True:
        try:
            line = inStream.readline()
            break
        except UnicodeDecodeError:
            print('Skipping invalid packet')
            continue

    valid = False
    last_move = -1000000.0
    last_blink = -1000000.0
    attention = meditation = 0
    motion = Motion.DOWN

    while line:
        try:
            packet = json.loads(line)
        except json.JSONDecodeError:
            continue  # ignore invalid packets

        # process blinks
        if 'blinkStrength' in packet and packet['blinkStrength'] > blink_threshold:
            print(f"Blink, last was {time.time() - last_blink} seconds ago")
            # detect double blink and change magnet
            if (time.time() - last_blink) < 3:
                last_blink = -1000000.0
                robot.change_magnet_state()
                print(f'Changed magnet state, magnet is now {"on" if robot.is_magnet_active else "off"}')
            else:
                last_blink = time.time()

        # process general data packets
        if 'eSense' in packet:
            refresh_stop_task()  # new packet, delay cutoff
            attention = packet['eSense']['attention']
            meditation = packet['eSense']['meditation']

            # if both = 0, connection is not established yet or has been lost
            valid = (attention + meditation) > 0

        if time.time() - last_move > 2:
            last_move = time.time()

            if valid and not cut_off:
                if motion == Motion.DOWN:
                    if attention >= threshold:  # move down while attention over threshold
                        robot.move_down_abs()
                        print('Moving down')
                    else:
                        robot.stopj()
                        print(f'Stopping, attention at {attention}')
                else:
                    if motion == Motion.UP:
                        if meditation >= threshold:  # move up while meditation over threshold
                            robot.move_up_abs()
                            print('Moving up')
                        else:
                            robot.stopj()
                            print(f'Stopping, meditation at {meditation}')
                    else:  # unknown (or None) motion, stop
                        robot.stopj()
                        print('No motion, stopping')
            else:  # invalid state, stop
                robot.stopj()
                print('Waiting for valid data')

        if motion == Motion.DOWN and robot.is_down():
            robot.stopj()
            print('Stopping, switching to UP')
            motion = Motion.UP
        else:
            if motion == Motion.UP and robot.is_up():
                robot.stopj()
                print('Stopping, switching to DOWN')
                motion = Motion.DOWN
        try:
            line = inStream.readline()
        except UnicodeDecodeError as e:
            print(e)

    print('Connection closed, shutting down')
    robot.stopj()
