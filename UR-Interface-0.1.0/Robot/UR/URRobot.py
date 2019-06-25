from Communication.SocketConnection import SocketConnection
from Robot.UR.URModbusServer import URModbusServer
from Robot.UR.URScript import URScript
import math

import time


class URRobot:
    """
    Interface for communicating with the UR Robot
    SecondaryPort used for sending commands
    ModbusServer used for retrieving info
    """
    def __init__(self, host):
        self.secondaryPort = 30002
        self.secondaryInterface = SocketConnection(host, self.secondaryPort)
        self.secondaryInterface.connect()
        self.URModbusServer = URModbusServer(host)
        self.URScript = URScript()

        # variables that count how many times the arm moved to a certain direction
        self.right_moves = 0
        self.left_moves = 0
        self.down_moves = 0
        self.up_moves = 0
        self.forward_moves = 0
        self.backward_moves = 0

        # variable to memorize the state of the magnet: 1 if active, 0 otherwise
        self.is_magnet_active = 0

        # variables representing the minimum and maximum values for the X, Y & Z axis, which the arm can reach (in mm)
        self.max_x = 100.00  # to right, 2 moves
        self.max_y = -750.00
        self.max_z = 400
        self.min_x = -300.00  # to left, 2 moves
        self.min_y = -850.00
        self.min_z = 6

        # Max safe values of acceleration and velocity are 0.4
        # DO NOT USE THE FOLLOWING VALUES
        # MAX a=1.3962634015954636
        # MAX v=1.0471975511965976
        # These are the absolute max for 'safe' movement and should only be used
        # if you know what your are doing, otherwise you WILL damage the UR internally
        self.acceleration = 0.1
        self.velocity = 0.1

    def movel(self, pose, a=0.1, v=0.1, joint_p=False):
        """Move to position (linear in tool-space)

        See :class:`URScript` for detailed information
        """
        script = URScript.movel(pose, a, v, joint_p=joint_p).encode()
        return self._send_script(script)

    def movej(self, q, a=0.1, v=0.1, joint_p=True):
        """Move to position (linear in joint-space)

        See :class:`URScript` for detailed information
        """
        script = URScript.movej(q, a, v, joint_p=joint_p).encode()
        return self._send_script(script)

    def stopj(self, a=1.5):
        """Stop (linear in joint space)

        See :class:`URScript` for detailed information
        """
        script = URScript.stopj(a).encode()
        return self._send_script(script)

    def set_tcp(self, pose):
        """Set the Tool Center Point

        See :class:`URScript` for detailed information
        """
        script = URScript.set_tcp(pose).encode()
        return self._send_script(script)

    def get_tcp_position(self):
        """ Get TCP position

        Will return values as seen on the teaching pendant (300.0mm)
        :return: 6 Floats - Position data of TCP (x, y, z) in mm (Rx, Ry, Rz) in radials
        """
        position_data = self.URModbusServer.get_tcp_position()
        return position_data

    def set_io(self, io, value):
        """
        Set the specified IO
        :param io: The IO to set as INT
        :param value: Boolean to enable or disable IO
        :return: Boolean to check if the command has been send
        """
        script = "set_digital_out({}, {})".format(io, value) + "\n"
        script = script.encode()
        return self._send_script(script)

    def translate(self, vector, a=0.1, v=0.1):
        """ Move TCP based on its current position

        Example:
        If the TCP position was: 0.4, 0.5, 0.5 and the vector value passed is 0.1, 0.0, 0.0.
        It will attempt to translate the TCP to 0.5, 0.5, 0.5
        :param vector: the X, Y, Z to translate to
        :param a: tool acceleration [m/2^s]
        :param v: tool speed [m/s]
        :return: Boolean to check if the command has been send
        """
        tcp_pos = list(self.get_tcp_position())
        tcp_pos[0] = tcp_pos[0] / 1000 + vector[0]
        tcp_pos[1] = tcp_pos[1] / 1000 + vector[1]
        tcp_pos[2] = tcp_pos[2] / 1000 + vector[2]
        return self.movel(tcp_pos, a, v)

    def _send_script(self, _script):
        """ Send URScript to the UR controller

        :param _script: formatted script to send
        :return: Boolean to check if the script has been send
        """
        try:
            self.secondaryInterface.send(_script)
        except OSError as error:
            print("OS error: {0}".format(error))
            return False
        return True

    @ staticmethod
    def format_cartesian_data(cartesian_data):
        """
        Formats the vector from human readable mm to robot readable mm
        A position of 300mm becomes 0.3
        :param cartesian_data: data to format
        :return: formatted data
        """
        i = 0
        for position in cartesian_data:
            cartesian_data[position] /= (1000 if i < 3 else 1)
            i += 1
        formatted_cartesian_data = cartesian_data

        return formatted_cartesian_data

    """
    MY CODE STARTS HERE
    TO DO: -TEST THE NEW METHODS
           - DO I STILL NEED MOVEMENT COUNT?
           -FIND THE MIN AND MAX VALUES FOR X,Y,Z
           -HOW CAN I SEPARATE MY CODE FROM THE INTERFACE?
           -CAN I USE THE CAMERA TO DETECT HOW CLOSE THE ARM IS TO THE TABLE?
           -FIND A MORE ELEGANT WAY TO CHECK THE BOUNDARIES FOR MOVEMENTS??
           -KEEP IN MIND THAT THE POSITIONS MIGHT DIFFER IRL WITH 1 TO 10 MM, LEAVE ROOM FOR ERRORS IN BOUNDARY LIMITS
           -KEEP IN MIND THAT ONE COMMAND OVERWRITES THE OTHER, SO WAITING TIME IS NEEDED BETWEEN MOVEMENTS
    """

    def move_right(self):
        """
        Function to move the robot to the right 10cm, with a waiting time of 10 seconds between movements.
        """

        vector = list((0.10, 0.0, 0.0))
        if self.is_within_boundaries(vector) == 0:
            vector = self.recalculate_position(vector)
        if self.right_moves < 3 and self.is_within_boundaries(vector):
            self.translate(vector)
            self.right_moves += 1
            self.left_moves -= 1
            time.sleep(10)
        else:
            self.stopj()
            print("Stopped")

    def move_left(self):
        """
        Function to move the robot to the left 10cm, with a waiting time of 10 seconds between movements.
        """

        vector = list((-0.10, 0.0, 0.0))
        if self.is_within_boundaries(vector) == 0:
            vector = self.recalculate_position(vector)
        if self.left_moves < 3 and self.is_within_boundaries(vector):
            self.translate(vector)
            self.left_moves += 1
            self.right_moves -= 1
            time.sleep(10)
        else:
            self.stopj()
            print("Stopped")

    def move_up(self):
        """
        Function to move the robot up 9.8cm and backwards 2cm , with a waiting time of 10 seconds between movements.
        """

        vector = list((0.0, 0.02, 0.098))
        if self.is_within_boundaries(vector) == 0:
            vector = self.recalculate_position(vector)
        if self.up_moves < 2 and self.is_within_boundaries(vector):
            self.translate(vector)
            self.up_moves += 1
            self.down_moves -= 1
            time.sleep(10)
        else:
            self.stopj()
            print("Stopped")

    def move_down(self):
        """
        Function to move the robot down 9.8cm and forward 2cm, with a waiting time of 10 seconds between movements.
        """

        vector = list((0.0, -0.02, -0.098))
        if self.is_within_boundaries(vector) == 0:
            vector = self.recalculate_position(vector)
        if self.down_moves < 3 and self.is_within_boundaries(vector):
            self.translate(vector)
            self.down_moves += 1
            self.up_moves -= 1
            time.sleep(10)
        else:
            self.stopj()
            print("Stopped")

    def move_forward(self):
        """
        Function to move the robot forward with 5 cm, with a waiting time of 10 seconds between movements.
        """

        vector = list((0.0, -0.05, 0.0))
        if self.is_within_boundaries(vector) == 0:
            vector = self.recalculate_position(vector)
        if self.forward_moves < 3 and self.is_within_boundaries(vector):
            self.translate(vector)
            self.forward_moves += 1
            self.backward_moves -= 1
            time.sleep(10)
        else:
            self.stopj()
            print("Stopped")

    def move_backward(self):
        """
        Function to move the robot backwards 5 cm, with a waiting time of 10 seconds between movements.
        """

        vector = list((0.0, 0.05, 0.0))
        if self.is_within_boundaries(vector) == 0:
            vector = self.recalculate_position(vector)
        if self.backward_moves < 3 and self.is_within_boundaries(vector):
            self.translate(vector)
            self.backward_moves += 1
            self.forward_moves -= 1
            time.sleep(10)
        else:
            self.stopj()
            print("Stopped")

    def change_magnet_state(self):
        """
        Activate the magnet if it is off or deactivate it if it's on
        """

        if self.is_magnet_active == 0:
            self.set_io(8, True)
        else:
            self.set_io(8, False)

    def refresh_movement_count(self):
        """
        Refreshes the values of the movements count of the arm
        This is the equivalent of the arm being in the default position
        :return:
        """

        self.right_moves = 0
        self.left_moves = 0
        self.down_moves = 0
        self.up_moves = 0
        self.forward_moves = 0
        self.backward_moves = 0

    def reset_position(self):
        """
        Resets the position of the robotic arm back to a default position.
        """

        self.set_tcp((0.05, -0.05, 0.295, 0, 0, 0))
        time.sleep(0.5)
        self.movel((-0.1, -0.8, 0.3, 0, 3.14, 0))
        self.refresh_movement_count()
        time.sleep(7)

    def get_x_position(self):
        """
        Returns the value in m of the arm's position on the X axis
        :return: the position on the X axis
        """

        pose = list(self.get_tcp_position())
        x_position = pose[0]
        return x_position

    def next_position(self, vector):
        """
        Calculates what the position of the arm would be if it would translate according
        to the values given in the vector parameter
        :param vector: Three floating point values representing distances in m (X, Y, Z)
        :return: the position after the translation
        """

        tcp_pos = list(self.get_tcp_position())
        tcp_pos[0] = tcp_pos[0] / 1000 + vector[0]
        tcp_pos[1] = tcp_pos[1] / 1000 + vector[1]
        tcp_pos[2] = tcp_pos[2] / 1000 + vector[2]
        return tcp_pos

    def is_within_boundaries(self, vector):
        """
        Function to check if a certain movement will result in the arm crossing the set boundaries.
        :param vector: Three floating point values representing distances in m (X, Y, Z)
        :return: 1, if the movement is possible, 0 if not
        """

        next_position = self.next_position(vector)
        x = next_position[0] * 1000
        y = next_position[1] * 1000
        z = next_position[2] * 1000
        print("The next position would be: x={}, y={}, z={}".format(x, y, z))
        if self.min_x < x < self.max_x and self.min_y < y < self.max_y and self.min_z < z < self.max_z:
            print("Inside boundaries")
            return 1
        else:
            print("Outside boundaries")
            return 0

    def recalculate_position(self, vector):
        """
        This function recalculates the position to which the arm can move without crossing the set boundaries,
        given a vector of values
        :param vector: The values for movement on the X, Y, Z axis
        :return: 3 floating point values representing the new values for th X, Y, Z movement
        """
        next_position = self.next_position(vector)
        current_position = self.get_tcp_position()
        next_x = next_position[0] * 1000
        next_y = next_position[1] * 1000
        next_z = next_position[2] * 1000
        if next_x < self.min_x:
            next_x = (self.min_x - current_position[0]) / 1000
        elif next_x > self.max_x:
            next_x = (self.max_x - current_position[0]) / 1000
        if next_y < self.min_y:
            next_y = (self.min_y - current_position[1]) / 1000
        elif next_y > self.max_y:
            next_y = (self.max_y - current_position[1]) / 1000
        if next_z < self.min_z:
            next_z = (self.min_z - current_position[2]) / 1000
        elif next_z > self.max_z:
            next_z = (self.max_z - current_position[2]) / 1000
        # vector = list(next_x, next_y, next_z)
        return next_x, next_y, next_z

    def go_to_starting_position(self):
        """
        The starting point for the arm when drawing figures
        """
        self.movel((-0.1, -0.8, 0.3, 0, 3.14, 0))

    def draw_square(self):
        """
        Funciton for the arm to move in a square shape
        """
        self.go_to_starting_position()  # A
        print("A")
        length = 0.1
        time.sleep(5)
        self.translate((0, -length, 0))  # B
        print("B")
        time.sleep(5)
        self.translate((length, 0, 0))  # C
        print("C")
        time.sleep(5)
        self.translate((0, length, 0))  # D
        print("D")
        time.sleep(5)
        self.go_to_starting_position()  # go back to A
        print("Back to A")

    def draw_rectangle(self):
        """
        Funciton for the arm to move in a rectangle shape
        """
        self.go_to_starting_position()  # A
        length = 0.1
        width = 0.25
        time.sleep(10)
        self.translate((0, -length, 0))  # B
        time.sleep(10)
        self.translate((width, 0, 0))  # C
        time.sleep(10)
        self.translate((0, length, 0))  # D
        time.sleep(10)
        self.go_to_starting_position()  # go back to A

    def draw_triangle(self):
        """
        Function for the arm to move in a triangle shape
        """
        self.go_to_starting_position()  # A
        base_length = 0.3
        side_length = 0.2
        x_b = base_length/2
        y_b = math.sqrt(math.pow(side_length, 2) - math.pow(base_length/2), 2)
        time.sleep(10)
        print("A")
        self.translate((-x_b, -y_b, 0))
        time.sleep(10)
        print("B")
        self.translate((x_b, 0, 0))
        time.sleep(10)
        print("C")
        self.go_to_starting_position()
        time.sleep(10)
        print("Back to A")

    def move_to_pose(self):
        """
        Function to move the arm as long as the attention level is over 75
        Might need changes after testing with the headset
        """
        attention_level = 75
        while attention_level >= 75:
            vector = list((0.05, 0.05, 0.05))
            if self.is_within_boundaries(vector) == 0:
                vector = self.recalculate_position(vector)
            if self.is_within_boundaries(vector):
                self.translate(vector)
                time.sleep(3)
            else:
                self.stopj()
                time.sleep(10)
                self.reset_position()

    def move_down_abs(self):
        self.movel((-0.080, -0.90, 0.073, 0, 3.14, 0))

    def move_up_abs(self):
        self.movel((-0.1, -0.8, 0.3, 0, 3.14, 0))










