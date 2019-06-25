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
# TODO change configurations as needed
robot.set_tcp((0.05, -0.05, 0.295, 0, 0, 0))
time.sleep(0.5)

# Set the starting position ((-0.10, -0.8, 0.3, 0, 3.14, 0))
robot.reset_position()


class Motion(Enum):
    DOWN = auto()
    UP = auto()


threshold = 60
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
        robot.stopj()

    if stopTask is not None:
        stopTask.cancel()

    stopTask = Timer(5.0, stop)
    stopTask.start()
    cut_off = False


if __name__ == '__main__':
    # open connection to the headset
    headset = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    headset.connect(('localhost', 13854))

    # send configuration and start listening
    headset.send(bytes("{\"enableRawData\": false, \"format\": \"Json\"", 'ascii'))
    time.sleep(1)

    inStream = headset.makefile(encoding='utf8')
    line = inStream.readline()

    valid = False
    last_blink = -1000000.0
    attention = meditation = 0
    motion = Motion.DOWN

    while line:
        try:
            packet = json.loads(line)
        except json.JSONDecodeError:
            continue  # ignore invalid packets

        # process blinks
        if hasattr(packet, 'blinkStrength') and packet.blinkStrength > 30:

            # detect double blink and change magnet
            if time.time() - last_blink < 1:
                last_blink = -1000000.0
                robot.change_magnet_state()
            else:
                last_blink = time.time()

        # process general data packets
        if hasattr(packet, 'eSense'):
            refresh_stop_task()  # new packet, delay cutoff
            attention = packet.eSense.attention
            meditation = packet.eSense.meditation

            # if both = 0, connection is not established yet or has been lost
            valid = (attention + meditation) > 0

        if valid and not cut_off:
            if motion == Motion.DOWN:
                if attention >= threshold:  # move down while attention over threshold
                    robot.move_up()  # TODO replace with move to start
                else:
                    robot.stopj()
            else:
                if motion == Motion.DOWN:
                    if meditation >= threshold:  # move up while meditation over threshold
                        robot.move_down()  # TODO replace with move to table
                    else:
                        robot.stopj()
                else:  # unknown (or None) motion, stop
                    robot.stopj()
        else:  # invalid state, stop
            robot.stopj()

        if motion == Motion.DOWN and False:  # TODO detect near table
            robot.stopj()
            motion = Motion.UP
        else:
            if False:  # TODO detect near starting point
                robot.stopj()
                motion = Motion.DOWN

        line = inStream.readline()
