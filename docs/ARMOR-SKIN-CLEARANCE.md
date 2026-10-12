# Beta.23: worn armor clearance

The supplied Reforged base shells use inflation near 0.255–0.275 model pixels, very close to the vanilla outer skin layer. The user's client also contains Skin Layers 3D with extruded jackets, sleeves and pants. Its documented compatibility issue is skin extrusion protruding through armor that is closer to the body than vanilla armor. This matches the remaining client symptom but needs direct client confirmation.

Only positive-volume, inflated box-UV base shells get an optional worn vertex set with at least 0.75 model pixels of clearance. Original mesh vertices, icon geometry, PNGs, UVs, ornaments, item IDs and statistics stay intact. The worn renderer uses the alternate vertices; older meshes without the field retain their original rendering. Shaders and Skin Layers configuration are unchanged.

Original source geometry still passes the seven-model face audit. The packaged validator checks finite alternate vertices, preserved UVs and unchanged inventory bounds. This is a compatibility adjustment awaiting visual confirmation with the user's shaders, not a claim of fully fixed client rendering.
