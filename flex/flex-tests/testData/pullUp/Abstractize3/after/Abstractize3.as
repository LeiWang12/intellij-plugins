package {
interface IFoo {

    /**
     *
     * @param p p
     * @param s s
     * @param r r
     * @return return
     */
    function foo(p:int, s:String, ...r):Number;
}

public class Sub implements IFoo {
    public function foo(p:int, s:String, ...r):Number {
        var v = 0;
        return 0;
    }
}

}
