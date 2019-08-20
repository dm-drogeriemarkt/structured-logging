package de.dm.prom.structuredlogging;

public final class ExampleBeanId implements MdcContextId<ExampleBean> {
    @Override
    public String getMdcKey() {
        return "example_bean";
    }
}
