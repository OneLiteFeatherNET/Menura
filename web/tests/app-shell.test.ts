import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import App from '../src/App.vue';

describe('app shell', () => {
  it('renders a top app bar that shows the product name', () => {
    const wrapper = mount(App);

    expect(wrapper.find('header').text()).toContain('Menura');
  });

  it('renders an empty main content area below the app bar', () => {
    const wrapper = mount(App);

    expect(wrapper.find('main').exists()).toBe(true);
  });
});
