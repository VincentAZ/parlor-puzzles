#!/usr/bin/env python3
"""Original fixed-seed Traffic Jam prototype authoring; never runs in the app.

Uniform-cost breadth-first search proves pars under the shipping whole-translation
move convention. Kotlin independently replays and proves every committed result.
No commercial boards or level lists are input to this generator.
"""
from collections import deque
from pathlib import Path
import json
import random

SEED = 20260928
MAX_STATES = 150_000
RANGES = ((8, 10), (11, 15), (16, 21), (22, 60))


def solve(pieces, positions):
    # piece tuple: (horizontal, fixed coordinate, length)
    parent = {positions: None}
    queue = deque([positions])
    while queue:
        state = queue.popleft()
        occupied = [0] * 36
        for i, ((horizontal, fixed, length), pos) in enumerate(zip(pieces, state)):
            for k in range(length):
                x, y = (pos + k, fixed) if horizontal else (fixed, pos + k)
                occupied[y * 6 + x] = i + 1
        for i, (horizontal, fixed, length) in enumerate(pieces):
            pos = state[i]
            for direction in (-1, 1):
                dest = pos
                while True:
                    cell = dest - 1 if direction < 0 else dest + length
                    if cell < 0 or cell >= 6:
                        if i == 0 and direction == 1:
                            path = [(i, 6)]
                            cursor = state
                            while parent[cursor] is not None:
                                cursor, move = parent[cursor]
                                path.append(move)
                            return path[::-1], len(parent)
                        break
                    index = fixed * 6 + cell if horizontal else cell * 6 + fixed
                    if occupied[index] not in (0, i + 1):
                        break
                    dest += direction
                    next_state = state[:i] + (dest,) + state[i+1:]
                    if next_state in parent:
                        continue
                    if len(parent) >= MAX_STATES:
                        return None, len(parent)
                    parent[next_state] = (state, (i, dest))
                    queue.append(next_state)
    return None, len(parent)


def generate():
    rng = random.Random(SEED)
    buckets = [[] for _ in RANGES]
    seen = set()
    candidate = 0
    while any(len(bucket) < 3 for bucket in buckets):
        candidate += 1
        pieces = [(True, 2, 2)]
        positions = [rng.randrange(3)]
        occupied = {(positions[0] + k, 2) for k in range(2)}
        options = [(h, fixed, length, pos) for h in (True, False) for fixed in range(6)
                   for length in (2, 3) for pos in range(7-length)]
        rng.shuffle(options)
        for horizontal, fixed, length, pos in options:
            cells = {(pos+k, fixed) if horizontal else (fixed, pos+k) for k in range(length)}
            if not occupied.intersection(cells):
                pieces.append((horizontal, fixed, length))
                positions.append(pos)
                occupied.update(cells)
            if len(pieces) >= 12:
                break
        path, visited = solve(pieces, tuple(positions))
        if not path:
            continue
        cost = len(path)
        tier = next((i for i, (low, high) in enumerate(RANGES) if low <= cost <= high), None)
        if tier is None or len(buckets[tier]) == 3:
            continue
        canonical = tuple(sorted(zip(pieces[1:], positions[1:]))) + ((pieces[0], positions[0]),)
        if canonical in seen:
            continue
        seen.add(canonical)
        order = len(buckets[tier]) + 1
        level = {
            'id': f'traffic-{tier * 20 + order:03d}', 'revision': 1,
            'puzzle': 'TRAFFIC_JAM', 'variant': 'standard', 'tier': ['EASY','MEDIUM','HARD','EXPERT'][tier],
            'order': order,
            'initial': {'type':'traffic_jam', 'width':6, 'height':6, 'exitRow':2, 'targetId':'A', 'pieces':[
                {'id':chr(65+i), 'x':pos if h else fixed, 'y':fixed if h else pos, 'length':length,
                 'axis':'HORIZONTAL' if h else 'VERTICAL'}
                for i, ((h, fixed, length), pos) in enumerate(zip(pieces, positions))]},
            'reference':[{'type':'translate','pieceId':chr(65+i),'to':to} for i,to in path],
            'par':cost, 'parKind':'PROVEN_OPTIMAL', 'targetTimeMs':max(60_000, cost * (8_000+2_000*tier)),
            'scoringUnit':'MOVES','proVariant':False,
            'provenance': {'author':'Parlor Puzzles','method':f'Independent random packing + unit-cost BFS; candidate {candidate}; {visited} visited states',
                           'seed':SEED, 'license':'Original project content; no third-party layout source'},
        }
        buckets[tier].append(level)
        print(f"Accepted {level['id']}: {cost} moves, candidate {candidate}, {visited} states", flush=True)
    output = Path(__file__).resolve().parents[2] / 'app/src/main/assets/levels/traffic-jam.json'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps({'schemaVersion':1,'contentVersion':1,'levels':sum(buckets,[])},indent=2)+'\n')
    print(f'Wrote {output}', flush=True)


if __name__ == '__main__':
    generate()
