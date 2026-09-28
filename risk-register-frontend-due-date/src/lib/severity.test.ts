import { describe, expect, it } from 'vitest';
import { inherentScore, severityFor } from './severity';

describe('severityFor', () => {
  it.each([[1, 'LOW'], [5, 'LOW'], [6, 'MEDIUM'], [12, 'MEDIUM'], [13, 'HIGH'], [19, 'HIGH'], [20, 'CRITICAL'], [25, 'CRITICAL']])(
    'score %i -> %s', (score, expected) => expect(severityFor(score)).toBe(expected));
});

describe('inherentScore', () => {
  it('is likelihood x impact', () => {
    expect(inherentScore(4, 5)).toBe(20);
    expect(inherentScore(1, 1)).toBe(1);
    expect(inherentScore(5, 5)).toBe(25);
  });
});
