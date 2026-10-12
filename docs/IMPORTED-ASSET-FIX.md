# Beta.21: atlas and inventory icon correction

The client log identified missing block-atlas sprites for every imported PNG and armor particle, even though those files were present in the JAR. Minecraft's block atlas does not discover arbitrary texture directories automatically. An additive `assets/minecraft/atlases/blocks.json` directory source now includes `imported` and `models/armor`, preserving existing vanilla/mod atlas sources and original PNG bytes.

Reforged inventory previews normalize using the mesh's bounding sphere rather than its longest unrotated axis, leaving margin after GUI rotation. Equipped armor geometry is unchanged. The three Dark Lightning weapon models use centered GUI transforms fitted to 12 pixels; held/equipped transforms and statistics are unchanged.

`python tools/validate_imported_assets.py <jar>` verifies actual packaged atlas coverage, every imported PNG reference, rotated weapon bounds and all 28 Reforged slot bounds. Client validation must confirm that the updated inventory has no missing sprites and that equipped armor still animates correctly.
