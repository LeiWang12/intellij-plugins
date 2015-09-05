package {
import org.flexunit.Assert;
public class FlexUnit4 {

    [Test]
    public function green() {
        Assert.assertEquals(1, 1);
    }

    [Test]
    public function red() {
        Assert.assertEquals(1, 2);
    }

    [Test]
    public function exception() {
        throw new Error("error");
    }

    public function notATest() {

    }
}
}

<testResults status="Assertion failed">
  <suite name="FlexUnit4" status="Assertion failed">
    <test name="green" status="Completed"/>
      <test name="exception" status="Assertion failed"/>
      <test name="red" status="Assertion failed"/>
  </suite>
</testResults>
