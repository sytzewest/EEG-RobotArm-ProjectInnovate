
import time

from Robot.UR.URRobot import URRobot

@staticmethod
def move_right(self):
    #function to move the robot to the right for a period of 2 seconds
    count = 0
    to_right_pos = 0.1
    while count < 3:
        count = count + 1
        self.translate(to_right_pos, 0.0, 0.0)
        time.sleep(1.5)





