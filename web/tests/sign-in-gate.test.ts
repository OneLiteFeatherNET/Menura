import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import SignInGate from '../src/auth/SignInGate.vue';

describe('sign-in gate screens', () => {
  it('offers the Microsoft sign-in link when the user is anonymous', () => {
    const wrapper = mount(SignInGate, { props: { state: { status: 'anonymous' } } });

    const button = wrapper.find('md-filled-button');
    expect(button.text()).toBe('Mit Microsoft anmelden');
    expect(button.attributes('href')).toBe('/auth/login');
  });

  it('explains the missing role when the user is signed in without access', () => {
    const wrapper = mount(SignInGate, { props: { state: { status: 'forbidden' } } });

    expect(wrapper.text()).toContain('Kein Zugriff – dir fehlt eine Menura-Rolle');
    expect(wrapper.find('md-filled-button').exists(), 'no sign-in button').toBe(false);
  });

  it('shows a progress note while the session is checked', () => {
    const wrapper = mount(SignInGate, { props: { state: { status: 'loading' } } });

    expect(wrapper.text()).toContain('Anmeldung wird geprüft');
  });

  it('shows the error message when the session cannot be checked', () => {
    const wrapper = mount(SignInGate, {
      props: { state: { status: 'error', message: 'Der Server ist gerade nicht erreichbar.' } },
    });

    expect(wrapper.find('[role="alert"]').text()).toBe('Der Server ist gerade nicht erreichbar.');
  });
});
