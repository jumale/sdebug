package com.github.jumale.sdebug.slick

import com.github.jumale.sdebug.Palette
import org.scalatest.wordspec.AnyWordSpec

class SdebugSlickSupportTest extends AnyWordSpec {
  "formatAnalyzeOutput" should {
    "colorize costs" in {
      val output = Seq(
        "Sort  (cost=71.24..71.25 rows=2 width=56) (actual time=0.749..0.752 rows=40 loops=1)",
        "  Sort Key: x2.parent_order",
        "  Sort Method: quicksort  Memory: 28kB",
        "  ->  Nested Loop  (cost=17.65..71.23 rows=2 width=56) (actual time=0.108..0.729 rows=40 loops=1)",
        "        ->  Seq Scan on workspaces  (cost=0.00..4.89 rows=1 width=16) (actual time=0.011..0.027 rows=1 loops=1)",
        "              Filter: ((team_id = '00000000-0000-0000-0000-000000000001'::uuid) AND (id = '87754e63-d162-4f3e-a78e-abeee6cadd93'::uuid))",
        "              Rows Removed by Filter: 125",
        "        ->  Nested Loop  (cost=17.65..66.32 rows=2 width=72) (actual time=0.095..0.696 rows=40 loops=1)",
        "              ->  Hash Join  (cost=17.37..64.10 rows=5 width=72) (actual time=0.080..0.574 rows=40 loops=1)",
        "                    Hash Cond: (x2.parent_id = x6.id)",
        "                    ->  Seq Scan on item_relations x2  (cost=0.00..41.37 rows=2037 width=56) (actual time=0.017..0.365 rows=2009 loops=1)",
        "                    ->  Hash  (cost=17.32..17.32 rows=4 width=32) (actual time=0.054..0.055 rows=20 loops=1)",
        "                          Buckets: 1024  Batches: 1  Memory Usage: 10kB",
        "                          ->  Bitmap Heap Scan on items x6  (cost=4.32..17.32 rows=4 width=32) (actual time=0.035..0.049 rows=20 loops=1)",
        "                                Recheck Cond: (workspace_id = '87754e63-d162-4f3e-a78e-abeee6cadd93'::uuid)",
        "                                Filter: (NOT archived)",
        "                                Heap Blocks: exact=4",
        "                                ->  Bitmap Index Scan on idx_items_workspace_id_archived  (cost=0.00..4.32 rows=4 width=0) (actual time=0.017..0.017 rows=20 loops=1)",
        "                                      Index Cond: ((workspace_id = '87754e63-d162-4f3e-a78e-abeee6cadd93'::uuid) AND (archived = false))",
        "              ->  Index Scan using pk_items on items x24  (cost=0.28..0.44 rows=1 width=16) (actual time=0.003..0.003 rows=1 loops=40)",
        "                    Index Cond: (id = x2.child_id)",
        "                    Filter: (NOT archived)",
        "Planning Time: 1.620 ms",
        "Execution Time: 0.809 ms",
      )




      println(SdebugSlickSupport.formatAnalyzeOutput(output, Palette.debug))
    }
  }
}
