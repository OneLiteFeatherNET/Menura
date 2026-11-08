import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import TranscriptView from '../src/features/pool/TranscriptView.vue';
import { countMatches, splitHighlight } from '../src/features/pool/highlight';
import type { Person, TranscriptSegment } from '../src/features/pool/types';

const PEOPLE: Person[] = [
  { id: 'p-a', name: 'Demo-Person A' },
  { id: 'p-b', name: 'Demo-Person B' },
];

const SEGMENTS: TranscriptSegment[] = [
  { startSeconds: 300, endSeconds: 360, personId: 'p-b', text: 'Der Horizontal Pod Autoscaler liest Metriken.' },
  { startSeconds: 900, endSeconds: 980, personId: 'p-a', text: 'Ohne Requests kann der Autoscaler nicht rechnen.' },
  { startSeconds: 1800, endSeconds: 1870, personId: null, text: 'Ohne Namen gesprochen.' },
];

describe('transcript segments', () => {
  it('shows each segment with its timestamp, person name and text', () => {
    const wrapper = mount(TranscriptView, { props: { segments: SEGMENTS, people: PEOPLE, query: '' } });

    const rows = wrapper.findAll('li');
    expect(rows).toHaveLength(3);
    expect(rows[0]?.text()).toContain('05:00');
    expect(rows[0]?.text()).toContain('Demo-Person B');
    expect(rows[0]?.text()).toContain('Der Horizontal Pod Autoscaler liest Metriken.');
  });

  it('names a line without a person as not assigned', () => {
    const wrapper = mount(TranscriptView, { props: { segments: SEGMENTS, people: PEOPLE, query: '' } });

    expect(wrapper.findAll('li')[2]?.text()).toContain('ohne Person');
  });

  it('highlights every match of the search, ignoring case, and nothing else', () => {
    const wrapper = mount(TranscriptView, { props: { segments: SEGMENTS, people: PEOPLE, query: 'AUTOSCALER' } });

    const marks = wrapper.findAll('mark');
    expect(marks).toHaveLength(2);
    expect(marks.every((mark) => mark.text().toLowerCase() === 'autoscaler')).toBe(true);
  });

  it('shows no highlight without a search', () => {
    const wrapper = mount(TranscriptView, { props: { segments: SEGMENTS, people: PEOPLE, query: '' } });

    expect(wrapper.findAll('mark')).toHaveLength(0);
  });
});

describe('highlight helpers', () => {
  it('splits the text into plain and matching parts', () => {
    expect(splitHighlight('ein Test und Test', 'test')).toEqual([
      { text: 'ein ', match: false },
      { text: 'Test', match: true },
      { text: ' und ', match: false },
      { text: 'Test', match: true },
    ]);
  });

  it('treats the query as literal text, not as a pattern', () => {
    expect(splitHighlight('a.b a+b', 'a+b')).toEqual([
      { text: 'a.b ', match: false },
      { text: 'a+b', match: true },
    ]);
  });

  it('counts the matches across all segments', () => {
    expect(countMatches(SEGMENTS, 'autoscaler')).toBe(2);
    expect(countMatches(SEGMENTS, '')).toBe(0);
  });
});
