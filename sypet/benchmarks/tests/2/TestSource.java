public static boolean test() throws Throwable {
	cmu.symonster.MyPoint mp = new cmu.symonster.MyPoint(1, 2);
	cmu.symonster.Point p = convert(mp);

	return (p.getX() == 1);
}