# Beta.22: preserve Blockbench armor faces

Client feedback showed missing/misaligned equipped armor details despite correct PNGs. The converter had normalized reversed cube endpoints, losing inside-out lining geometry. It also advanced rotated face UVs in the opposite direction to Blockbench and applied multi-axis Euler rotations in reverse order.

The converter now retains signed cube extents, inflation/stretch and flat-face epsilon; rotates UV corners in Blockbench's direction; applies the Three.js XYZ matrix convention; and computes normals from transformed face geometry. Material statistics, item IDs, player data and source PNGs remain unchanged.

An independent audit against all seven supplied bbmodels checks all 1,896 exported faces, including 46 inverted cubes and 276 rotated UV faces, nested transformations, joint-local reconstruction and exact original PNG hashes. Build/unit tests and the 42-model packaged atlas/icon check pass. A mesh preview confirms complete geometry; actual equipped client rendering still requires confirmation.

Reference implementations: https://github.com/JannisX11/blockbench/blob/master/js/outliner/types/cube.js and https://github.com/JannisX11/blockbench/blob/master/js/util/three_custom.js. Run `python tools/audit_blockbench_armor.py <directory-with-original-bbmodels>` for the full source comparison.
