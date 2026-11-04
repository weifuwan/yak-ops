#!/bin/bash
while getopts t:c:p: opts; do
    case $opts in
        t) t=$OPTARG ;;
        c) c=$OPTARG ;;
        p) p=$OPTARG ;;
        ?) ;;
    esac
done


# Write your specific logic here

# Set the exit code according to your execution result, and alert needs to use it to judge the status of this alarm result


if  [ "$t" = "error msg title" ]
   then
     exit 12
fi
exit 0
exit 0