#!/bin/bash
rgbasm -o $1.o $1.s
rgblink -o $1.gb $1.o
