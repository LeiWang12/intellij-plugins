package {
public class Ref {
  public function Ref() {
    var t1 : Super;
    Super.uu = Super.v;

    var t2 = new Sub1();
    t2.foo();

    var t3 = new Sub2();
    t3.foo();
  }
}
}
