public boolean test() throws Throwable {
    java.lang.String url = "https://www.google.com/";
    java.lang.String title = getTitle(url);
    if ("Google".equals(title))
        return true;
    else 
        return false;
}
