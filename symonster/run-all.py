import sys
import os
import fnmatch
from contextlib import contextmanager
import subprocess

matches = []
for root, dirnames, filenames in os.walk('benchmarks/joda'):
    for filename in fnmatch.filter(filenames, '*.json'):
        matches.append(os.path.join(root, filename))


print matches
with open('result.txt', 'w') as resultFile:
    for path in matches:
        print (path)
        resultFile.write(path + "\n")
        Dargs = ['"' + path + ' temp.txt -c"', 
                '"' + path + ' temp.txt"',
                '"' + path + ' temp.txt -c -e"',
                '"' + path + ' temp.txt -c -cp"',
                '"' + path + ' temp.txt -e -cp"']
        for Darg in Dargs:
            cmd = "timeout 900s " + "ant symonster -Dargs=" + Darg + " > programoutput.txt"
	    print cmd
            print "process starts."
            # p = subprocess.Popen(cmd, shell=True, stdout=subprocess.PIPE)
	    os.system(cmd)
            print "process terminates"
            with open('temp.txt') as tempFile:
                resultFile.write(tempFile.read())
                resultFile.write("\n")
        print ("\n")
        resultFile.write("\n")




