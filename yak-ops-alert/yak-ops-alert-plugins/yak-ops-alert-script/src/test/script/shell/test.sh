while getopts t: opts; do
    case $opts in
        t) t=$OPTARG ;;
        ?) ;;
    esac
done

echo "$t"

exit 0
