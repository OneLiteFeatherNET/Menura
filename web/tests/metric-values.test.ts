import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import FirstJoins from '../src/components/FirstJoins.vue';
import ViewPercent from '../src/components/ViewPercent.vue';
import { formatViewPercent } from '../src/domain/format';

describe('estimate chip', () => {
  it('is shown next to an estimated first-join value', () => {
    const wrapper = mount(FirstJoins, { props: { value: 1.5, estimated: true } });

    expect(wrapper.find('.estimate-chip').exists(), 'estimate chip').toBe(true);
    expect(wrapper.text()).toContain('1,5');
  });

  it('is not shown next to a measured first-join value', () => {
    const wrapper = mount(FirstJoins, { props: { value: 7, estimated: false } });

    expect(wrapper.find('.estimate-chip').exists(), 'estimate chip').toBe(false);
    expect(wrapper.text()).toContain('7');
  });
});

describe('missing view percentage', () => {
  it('reads "nicht verfügbar" instead of 0 when the platform gives no value', () => {
    const wrapper = mount(ViewPercent, { props: { value: null } });

    expect(wrapper.text()).toBe('nicht verfügbar');
    expect(wrapper.text()).not.toContain('0');
  });

  it('formats a delivered value with one decimal place and a German comma', () => {
    expect(formatViewPercent(71.5)).toBe('71,5 %');
  });
});
