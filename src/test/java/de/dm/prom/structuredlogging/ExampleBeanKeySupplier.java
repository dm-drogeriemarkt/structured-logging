package de.dm.prom.structuredlogging;

public final class ExampleBeanKeySupplier implements MdcKeySupplier<ExampleBean> {
    @Override
    public String getMdcKey() {
        return "example_bean";
    }
}
